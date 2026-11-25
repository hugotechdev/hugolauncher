package tz.co.hugotech.launcher

import android.Manifest
import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* --------------------------------- Chat model --------------------------------- */

data class ToolCall(val id: String, val name: String, val args: String)

/**
 * One chat message. role = user | assistant | tool.
 * raw keeps the provider's own reply so follow-ups are sent back exactly.
 */
data class Msg(
    val role: String,
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String = "",
    val toolName: String = "",
    val raw: String = "",
    val rawProvider: String = ""
)

class Confirm(val title: String, val body: String, val result: CompletableDeferred<Boolean>)

/* ------------------------------ API keys (encrypted) ------------------------------ */

object Keys {
    private var prefs: SharedPreferences? = null

    private fun sp(context: Context): SharedPreferences {
        prefs?.let { return it }
        val p = try {
            val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "hugo_keys", master, context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("hugo_keys_plain", Context.MODE_PRIVATE)
        }
        prefs = p
        return p
    }

    val version = mutableStateOf(0)

    fun get(context: Context, provider: String): String = sp(context).getString(provider, "") ?: ""

    fun set(context: Context, provider: String, key: String) {
        sp(context).edit().putString(provider, key.trim()).apply()
        version.value++
    }
}

/* ------------------------------- Incoming messages ------------------------------- */

data class InboxItem(
    val id: String,
    val pkg: String,
    val app: String,
    val sender: String,
    val text: String,
    val time: Long,
    val reply: Notification.Action?
)

/** Messages seen in notifications (only while the assistant may read them). */
object Inbox {
    private val hints = listOf("whatsapp", "telegram", "messaging", "mms", "sms", "android.gm", "instagram", "facebook.orca", "signal", "outlook", "teams", "slack")
    val items = mutableStateOf<List<InboxItem>>(emptyList())
    private var counter = 0

    fun isMessaging(pkg: String) = hints.any { pkg.lowercase().contains(it) }

    fun capture(context: Context, sbn: StatusBarNotification) {
        val sp = context.getSharedPreferences("hugo", Context.MODE_PRIVATE)
        if (!sp.getBoolean("assistant_on", true) || !sp.getBoolean("ai_read_messages", true)) return
        if (System.currentTimeMillis() < sp.getLong("assistant_paused_until", 0L)) return
        if (!isMessaging(sbn.packageName)) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        if (text.isBlank()) return
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        val app = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        val list = items.value
        if (list.firstOrNull()?.let { it.sender == sender && it.text == text } == true) return
        counter++
        val item = InboxItem("m$counter", sbn.packageName, app, sender, text.take(600), System.currentTimeMillis(), reply)
        val dayAgo = System.currentTimeMillis() - 86_400_000L
        items.value = (listOf(item) + list).filter { it.time > dayAgo }.take(80)
    }

    fun clear() {
        items.value = emptyList()
    }

    fun reply(context: Context, id: String, text: String): Boolean {
        val item = items.value.firstOrNull { it.id == id } ?: return false
        val action = item.reply ?: return false
        return try {
            val inputs = action.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()
            inputs.forEach { bundle.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, bundle)
            action.actionIntent.send(context, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/* ------------------------------------ Tools ------------------------------------ */

object Tools {
    private fun p(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

    private fun tool(name: String, desc: String, props: JSONObject = JSONObject(), required: List<String> = emptyList()) =
        JSONObject().put("name", name).put("description", desc)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)))

    val all: List<JSONObject> = listOf(
        tool("get_today", "Today's date and time, the user's tasks, calendar events, reminders, phone mode and weather."),
        tool(
            "get_messages", "Recent incoming messages from WhatsApp, SMS, Telegram, email and others (last 24 hours). Each has an id for replying.",
            JSONObject().put("app", p("string", "Optional app name to filter, e.g. WhatsApp")).put("limit", p("integer", "How many, default 15"))
        ),
        tool(
            "reply_message", "Reply to one incoming message by its id (WhatsApp/Telegram/SMS quick reply). The app asks the user to confirm first.",
            JSONObject().put("message_id", p("string", "id from get_messages")).put("text", p("string", "reply text")), listOf("message_id", "text")
        ),
        tool(
            "send_sms", "Send an SMS to a contact name or phone number. The app asks the user to confirm first.",
            JSONObject().put("to", p("string", "contact name or number")).put("text", p("string", "message")), listOf("to", "text")
        ),
        tool(
            "send_whatsapp", "Open WhatsApp with a message ready for a contact name or number; the user taps send.",
            JSONObject().put("to", p("string", "contact name or number")).put("text", p("string", "message")), listOf("to", "text")
        ),
        tool("call", "Open the phone dialer for a contact name or number.", JSONObject().put("to", p("string", "contact name or number")), listOf("to")),
        tool("find_contact", "Find phone numbers for a contact name.", JSONObject().put("name", p("string", "name")), listOf("name")),
        tool(
            "add_task", "Add one of today's tasks (max 4 a day).",
            JSONObject().put("title", p("string", "task")).put("time", p("string", "optional time, e.g. 2:00 PM")), listOf("title")
        ),
        tool(
            "set_reminder", "Set a reminder notification. Give minutes_from_now OR at (24h HH:mm, today or tomorrow).",
            JSONObject().put("text", p("string", "what to remind")).put("minutes_from_now", p("integer", "minutes from now")).put("at", p("string", "time HH:mm")),
            listOf("text")
        ),
        tool("open_app", "Open an installed app by name.", JSONObject().put("name", p("string", "app name")), listOf("name")),
        tool(
            "set_phone_mode", "Switch phone mode: work, worship, rest, or auto (by time).",
            JSONObject().put("mode", p("string", "work | worship | rest | auto")), listOf("mode")
        ),
        tool("start_focus", "Start a focus timer.", JSONObject().put("minutes", p("integer", "default 25"))),
        tool("get_screen_time", "Minutes each app was used today, and social media total."),
        tool("remember", "Save a lasting fact about the user (names, preferences) for future chats.", JSONObject().put("fact", p("string", "the fact")), listOf("fact"))
    )

    private fun hasPerm(ctx: Context, perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun findContacts(ctx: Context, name: String): List<Contact> {
        if (!hasPerm(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Contact>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null
            )?.use { c ->
                while (c.moveToNext() && out.size < 6) out.add(Contact(c.getString(0) ?: "", c.getString(1) ?: ""))
            }
        } catch (e: Exception) {
            // no access
        }
        return out.distinctBy { it.number.filter { ch -> ch.isDigit() } }
    }

    /** Returns (display name, number) or null. */
    private fun resolve(s: LauncherState, to: String): Pair<String, String>? {
        val digits = to.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 7) return to to digits
        s.contacts.value.firstOrNull { it.name.contains(to, true) }?.let { return it.name to it.number }
        val found = findContacts(s.context, to).firstOrNull() ?: return null
        return found.name to found.number
    }

    private fun waNumber(n: String): String {
        var d = n.filter { it.isDigit() }
        if (d.startsWith("0") && d.length == 10) d = "255" + d.drop(1)
        return d
    }

    private fun fmtTime(t: Long) = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(t))

    /** Runs one tool and returns a short text result for the AI. */
    suspend fun run(s: LauncherState, name: String, args: JSONObject, confirm: suspend (String, String) -> Boolean): String {
        val ctx = s.context
        return try {
            when (name) {
                "get_today" -> {
                    val now = System.currentTimeMillis()
                    val o = JSONObject()
                        .put("now", SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date(now)))
                        .put("phone_mode", s.effectiveMode())
                        .put("tasks", JSONArray(s.tasks.value.map { JSONObject().put("title", it.title).put("time", it.time).put("done", it.done) }))
                        .put("calendar", JSONArray(CalendarData.today(ctx).map { (if (it.allDay) "all day" else fmtTime(it.begin)) + " " + it.title }))
                        .put("reminders", JSONArray(Reminders.pending(ctx).map { fmtTime(it.first) + " " + it.second }))
                    WeatherService.cached(s)?.let { o.put("weather", "${it.temp}°C in ${it.city}") }
                    o.toString()
                }
                "get_messages" -> {
                    if (!s.readMessages.value) return "Reading messages is turned off in settings."
                    if (!s.music.hasAccess()) return "No notification access. Ask the user to turn on Music controls & badges in Settings."
                    val app = args.optString("app", "")
                    val limit = args.optInt("limit", 15).coerceIn(1, 40)
                    val list = Inbox.items.value.filter { app.isBlank() || it.app.contains(app, true) }.take(limit)
                    if (list.isEmpty()) "No messages received since the assistant started listening."
                    else JSONArray(list.map {
                        JSONObject().put("id", it.id).put("app", it.app).put("from", it.sender).put("text", it.text)
                            .put("time", fmtTime(it.time)).put("can_reply", it.reply != null)
                    }).toString()
                }
                "reply_message" -> {
                    val id = args.getString("message_id")
                    val text = args.getString("text")
                    val item = Inbox.items.value.firstOrNull { it.id == id } ?: return "Message not found."
                    if (item.reply == null) return "That app doesn't allow quick reply. Suggest opening it."
                    if (!confirm(s.tr("Reply to ${item.sender} (${item.app})?", "Jibu ${item.sender} (${item.app})?"), text)) return "User cancelled."
                    if (Inbox.reply(ctx, id, text)) "Reply sent." else "Couldn't send the reply."
                }
                "send_sms" -> {
                    val (who, number) = resolve(s, args.getString("to")) ?: return "Contact not found. Ask for the number, or contacts permission may be off."
                    val text = args.getString("text")
                    if (!hasPerm(ctx, Manifest.permission.SEND_SMS)) return "SMS permission is off. Ask the user to allow it in Assistant settings."
                    if (!confirm(s.tr("Send SMS to $who ($number)?", "Tuma SMS kwa $who ($number)?"), text)) return "User cancelled."
                    @Suppress("DEPRECATION")
                    val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                    sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
                    "SMS sent to $who."
                }
                "send_whatsapp" -> {
                    val (who, number) = resolve(s, args.getString("to")) ?: return "Contact not found. Ask for the number."
                    val text = args.getString("text")
                    if (!confirm(s.tr("Open WhatsApp to $who?", "Fungua WhatsApp kwa $who?"), text)) return "User cancelled."
                    val uri = Uri.parse("https://wa.me/" + waNumber(number) + "?text=" + URLEncoder.encode(text, "UTF-8"))
                    val i = Intent(Intent.ACTION_VIEW, uri)
                    val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
                    if (pkg != null) i.setPackage(pkg)
                    AppRepository.safeStart(ctx, i)
                    "WhatsApp opened with the message; the user taps send."
                }
                "call" -> {
                    val (who, number) = resolve(s, args.getString("to")) ?: return "Contact not found."
                    Controls.dial(ctx, number)
                    "Dialer opened for $who."
                }
                "find_contact" -> {
                    if (!hasPerm(ctx, Manifest.permission.READ_CONTACTS)) return "Contacts permission is off."
                    val list = findContacts(ctx, args.getString("name"))
                    if (list.isEmpty()) "No contact found." else JSONArray(list.map { "${it.name}: ${it.number}" }).toString()
                }
                "add_task" -> {
                    if (s.tasks.value.size >= 4) return "Today already has 4 tasks (the maximum)."
                    s.tasks.value = s.tasks.value + Task(args.getString("title"), args.optString("time", ""), false)
                    "Task added."
                }
                "set_reminder" -> {
                    val text = args.getString("text")
                    val at: Long = when {
                        args.has("minutes_from_now") -> System.currentTimeMillis() + args.getInt("minutes_from_now") * 60_000L
                        args.has("at") -> {
                            val (h, m) = args.getString("at").split(":").map { it.trim().toInt() }
                            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
                            if (c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1)
                            c.timeInMillis
                        }
                        else -> return "Need minutes_from_now or at."
                    }
                    Reminders.add(ctx, at, text)
                    "Reminder set for " + SimpleDateFormat("EEE HH:mm", Locale.ENGLISH).format(Date(at)) + "."
                }
                "open_app" -> {
                    val q = args.getString("name")
                    val app = s.apps.value.firstOrNull { it.label.equals(q, true) } ?: s.apps.value.firstOrNull { it.label.contains(q, true) }
                    if (app == null) "No app named $q." else {
                        AppRepository.open(s, app)
                        "Opened ${app.label}."
                    }
                }
                "set_phone_mode" -> {
                    when (val m = args.getString("mode").lowercase()) {
                        "auto" -> s.autoMode.value = true
                        "work", "worship", "rest" -> {
                            s.autoMode.value = false
                            s.phoneMode.value = m
                        }
                        else -> return "Unknown mode."
                    }
                    "Mode set."
                }
                "start_focus" -> {
                    val min = args.optInt("minutes", 25).coerceIn(1, 180)
                    s.focusEnd.longValue = System.currentTimeMillis() + min * 60_000L
                    "Focus started for $min minutes."
                }
                "get_screen_time" -> {
                    if (!Usage.hasAccess(ctx)) return "Usage access is off."
                    val u = Usage.todayMinutes(ctx).filterKeys { it != ctx.packageName }
                    val names = s.apps.value.associate { it.pkg to it.label }
                    JSONObject()
                        .put("total_minutes", u.values.sum())
                        .put("social_minutes", u.filterKeys { isSocial(it) }.values.sum())
                        .put("top", JSONArray(u.entries.sortedByDescending { it.value }.take(8).map { "${names[it.key] ?: it.key}: ${it.value} min" }))
                        .toString()
                }
                "remember" -> {
                    s.aiMemory.value = (s.aiMemory.value + args.getString("fact")).takeLast(30)
                    "Saved."
                }
                else -> "Unknown tool."
            }
        } catch (e: Exception) {
            "Tool error: ${e.message}"
        }
    }
}

/* ---------------------------------- Providers ---------------------------------- */

private class Http(val code: Int, val body: String)

private fun post(url: String, headers: Map<String, String>, body: JSONObject): Http {
    val c = URL(url).openConnection() as HttpURLConnection
    c.requestMethod = "POST"
    c.connectTimeout = 20_000
    c.readTimeout = 90_000
    c.doOutput = true
    c.setRequestProperty("content-type", "application/json")
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    c.outputStream.use { it.write(body.toString().toByteArray()) }
    val code = c.responseCode
    val text = ((if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use(BufferedReader::readText)) ?: ""
    c.disconnect()
    return Http(code, text)
}

private fun apiError(h: Http): String {
    val msg = try {
        val o = JSONObject(h.body)
        o.optJSONObject("error")?.optString("message") ?: o.toString()
    } catch (e: Exception) {
        h.body.take(200)
    }
    return "Error ${h.code}: $msg"
}

/** Groups history into the shape each provider expects, then parses the reply. */
private object Providers {

    fun call(s: LauncherState, provider: String, key: String, system: String, history: List<Msg>): Msg = when (provider) {
        "openai" -> openai(s, key, system, history)
        "gemini" -> gemini(s, key, system, history)
        else -> claude(s, key, system, history)
    }

    private fun claude(s: LauncherState, key: String, system: String, history: List<Msg>): Msg {
        val messages = JSONArray()
        var i = 0
        while (i < history.size) {
            val m = history[i]
            when (m.role) {
                "user" -> messages.put(JSONObject().put("role", "user").put("content", m.text))
                "assistant" -> {
                    val content = if (m.rawProvider == "claude" && m.raw.isNotEmpty()) JSONArray(m.raw) else JSONArray().apply {
                        if (m.text.isNotBlank()) put(JSONObject().put("type", "text").put("text", m.text))
                        m.toolCalls.forEach { put(JSONObject().put("type", "tool_use").put("id", it.id).put("name", it.name).put("input", JSONObject(it.args))) }
                    }
                    messages.put(JSONObject().put("role", "assistant").put("content", content))
                }
                "tool" -> {
                    val blocks = JSONArray()
                    while (i < history.size && history[i].role == "tool") {
                        blocks.put(JSONObject().put("type", "tool_result").put("tool_use_id", history[i].toolCallId).put("content", history[i].text))
                        i++
                    }
                    messages.put(JSONObject().put("role", "user").put("content", blocks))
                    continue
                }
            }
            i++
        }
        val tools = JSONArray(Tools.all.map {
            JSONObject().put("name", it.getString("name")).put("description", it.getString("description")).put("input_schema", it.getJSONObject("parameters"))
        })
        val body = JSONObject().put("model", s.modelClaude.value).put("max_tokens", 1024).put("system", system).put("messages", messages).put("tools", tools)
        val h = post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body)
        if (h.code !in 200..299) return Msg("assistant", apiError(h))
        val content = JSONObject(h.body).getJSONArray("content")
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        for (j in 0 until content.length()) {
            val b = content.getJSONObject(j)
            when (b.optString("type")) {
                "text" -> text.append(b.optString("text"))
                "tool_use" -> calls.add(ToolCall(b.getString("id"), b.getString("name"), b.optJSONObject("input")?.toString() ?: "{}"))
            }
        }
        return Msg("assistant", text.toString(), calls, raw = content.toString(), rawProvider = "claude")
    }

    private fun openai(s: LauncherState, key: String, system: String, history: List<Msg>): Msg {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { m ->
            when (m.role) {
                "user" -> messages.put(JSONObject().put("role", "user").put("content", m.text))
                "assistant" -> {
                    val o = JSONObject().put("role", "assistant").put("content", if (m.text.isBlank()) JSONObject.NULL else m.text)
                    if (m.toolCalls.isNotEmpty()) {
                        o.put("tool_calls", JSONArray(m.toolCalls.map {
                            JSONObject().put("id", it.id).put("type", "function").put("function", JSONObject().put("name", it.name).put("arguments", it.args))
                        }))
                    }
                    messages.put(o)
                }
                "tool" -> messages.put(JSONObject().put("role", "tool").put("tool_call_id", m.toolCallId).put("content", m.text))
            }
        }
        val tools = JSONArray(Tools.all.map {
            JSONObject().put("type", "function").put(
                "function",
                JSONObject().put("name", it.getString("name")).put("description", it.getString("description")).put("parameters", it.getJSONObject("parameters"))
            )
        })
        val body = JSONObject().put("model", s.modelOpenai.value).put("messages", messages).put("tools", tools)
        val h = post("https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer $key"), body)
        if (h.code !in 200..299) return Msg("assistant", apiError(h))
        val msg = JSONObject(h.body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val calls = ArrayList<ToolCall>()
        msg.optJSONArray("tool_calls")?.let { a ->
            for (j in 0 until a.length()) {
                val t = a.getJSONObject(j)
                val f = t.getJSONObject("function")
                calls.add(ToolCall(t.getString("id"), f.getString("name"), f.optString("arguments", "{}").ifBlank { "{}" }))
            }
        }
        val text = if (msg.isNull("content")) "" else msg.optString("content")
        return Msg("assistant", text, calls)
    }

    private fun upperTypes(o: JSONObject): JSONObject {
        val out = JSONObject()
        o.keys().forEach { k ->
            when (val v = o.get(k)) {
                is JSONObject -> out.put(k, upperTypes(v))
                is String -> out.put(k, if (k == "type") v.uppercase() else v)
                else -> out.put(k, v)
            }
        }
        if (out.optJSONArray("required")?.length() == 0) out.remove("required")
        return out
    }

    private fun gemini(s: LauncherState, key: String, system: String, history: List<Msg>): Msg {
        val contents = JSONArray()
        var i = 0
        while (i < history.size) {
            val m = history[i]
            when (m.role) {
                "user" -> contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", m.text))))
                "assistant" -> {
                    val parts = if (m.rawProvider == "gemini" && m.raw.isNotEmpty()) JSONArray(m.raw) else JSONArray().apply {
                        if (m.text.isNotBlank()) put(JSONObject().put("text", m.text))
                        m.toolCalls.forEach { put(JSONObject().put("functionCall", JSONObject().put("name", it.name).put("args", JSONObject(it.args)))) }
                    }
                    contents.put(JSONObject().put("role", "model").put("parts", parts))
                }
                "tool" -> {
                    val parts = JSONArray()
                    while (i < history.size && history[i].role == "tool") {
                        parts.put(JSONObject().put("functionResponse", JSONObject().put("name", history[i].toolName).put("response", JSONObject().put("result", history[i].text))))
                        i++
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                    continue
                }
            }
            i++
        }
        val decls = JSONArray(Tools.all.map {
            val params = it.getJSONObject("parameters")
            val d = JSONObject().put("name", it.getString("name")).put("description", it.getString("description"))
            if (params.getJSONObject("properties").length() > 0) d.put("parameters", upperTypes(params))
            d
        })
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${s.modelGemini.value}:generateContent?key=" + URLEncoder.encode(key, "UTF-8")
        val h = post(url, emptyMap(), body)
        if (h.code !in 200..299) return Msg("assistant", apiError(h))
        val cand = JSONObject(h.body).optJSONArray("candidates")?.optJSONObject(0) ?: return Msg("assistant", "No answer.")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        for (j in 0 until parts.length()) {
            val p = parts.getJSONObject(j)
            if (p.has("text") && !p.optBoolean("thought", false)) text.append(p.getString("text"))
            p.optJSONObject("functionCall")?.let { f ->
                calls.add(ToolCall("g${System.nanoTime()}$j", f.getString("name"), f.optJSONObject("args")?.toString() ?: "{}"))
            }
        }
        return Msg("assistant", text.toString(), calls, raw = parts.toString(), rawProvider = "gemini")
    }
}

/* ---------------------------------- Agent ---------------------------------- */

object Agent {
    fun providerName(p: String) = when (p) {
        "openai" -> "ChatGPT"
        "gemini" -> "Gemini"
        else -> "Claude"
    }

    private fun system(s: LauncherState): String {
        val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        val facts = s.aiMemory.value
        return buildString {
            append("You are HUGO, the personal assistant inside the user's Android phone launcher (HUGO Launcher by HUGO TECH, Tanzania). ")
            append("Now: $now. Phone mode: ${s.effectiveMode()}. ")
            append("Reply in the language the user writes in (Swahili or English). Be short and practical. ")
            append("Use the tools to act: read messages, reply, send SMS or WhatsApp, add tasks, set reminders, open apps, change mode, check screen time. ")
            append("The app shows the user every outgoing message and asks for confirmation, so call send tools directly when asked. ")
            append("Never invent message contents or contacts; use the tools. When summarising messages, group by sender and keep it brief. ")
            append("If the user seems to use the phone or social media a lot, gently encourage balance without lecturing. ")
            if (facts.isNotEmpty()) append("Things the user asked you to remember: " + facts.joinToString("; ") + ". ")
        }
    }

    /** Sends the chat, runs any tools, and repeats until the AI gives a final answer. */
    suspend fun ask(s: LauncherState, text: String, confirm: suspend (String, String) -> Boolean) {
        val provider = s.aiProvider.value
        val key = Keys.get(s.context, provider)
        s.chat.add(Msg("user", text))
        if (!s.assistantActive()) {
            s.chat.add(Msg("assistant", s.tr("The assistant is paused. Turn it on to continue.", "Msaidizi amesitishwa. Mwashe ili uendelee.")))
            return
        }
        if (key.isBlank()) {
            s.chat.add(Msg("assistant", s.tr("Add your ${providerName(provider)} API key in Assistant settings first.", "Weka API key ya ${providerName(provider)} kwenye settings za Msaidizi kwanza.")))
            return
        }
        s.aiBusy.value = true
        try {
            repeat(6) {
                val history = trimmed(s.chat.toList())
                val reply = withContext(Dispatchers.IO) {
                    try {
                        Providers.call(s, provider, key, system(s), history)
                    } catch (e: Exception) {
                        Msg("assistant", s.tr("Couldn't reach ${providerName(provider)}. Check internet.", "Imeshindwa kufikia ${providerName(provider)}. Angalia internet."))
                    }
                }
                s.chat.add(reply)
                if (reply.toolCalls.isEmpty()) return
                for (tc in reply.toolCalls) {
                    val args = try {
                        JSONObject(tc.args)
                    } catch (e: Exception) {
                        JSONObject()
                    }
                    val result = Tools.run(s, tc.name, args, confirm)
                    s.chat.add(Msg("tool", result, toolCallId = tc.id, toolName = tc.name))
                }
            }
        } finally {
            s.aiBusy.value = false
        }
    }

    /** Keeps the last ~20 messages, starting at a user message so tool pairs stay intact. */
    private fun trimmed(all: List<Msg>): List<Msg> {
        if (all.size <= 20) return all
        var start = all.size - 20
        while (start < all.size && all[start].role != "user") start++
        return if (start >= all.size) all.takeLast(1) else all.subList(start, all.size)
    }
}

/** Short label for a tool step shown in the chat. */
fun toolLabel(s: LauncherState, m: Msg): String = when (m.toolName) {
    "get_messages" -> s.tr("Read messages", "Imesoma ujumbe")
    "reply_message" -> s.tr("Reply", "Jibu") + " · " + m.text
    "send_sms" -> "SMS · " + m.text
    "send_whatsapp" -> "WhatsApp · " + m.text
    "add_task" -> s.tr("Task", "Kazi") + " · " + m.text
    "set_reminder" -> s.tr("Reminder", "Kikumbusho") + " · " + m.text
    "open_app" -> m.text
    "get_today" -> s.tr("Checked today's schedule", "Imeangalia ratiba ya leo")
    "get_screen_time" -> s.tr("Checked screen time", "Imeangalia muda wa skrini")
    "set_phone_mode" -> s.tr("Phone mode changed", "Hali ya simu imebadilika")
    "start_focus" -> m.text
    "remember" -> s.tr("Remembered", "Imekumbuka")
    "find_contact" -> s.tr("Looked up contact", "Imetafuta mtu")
    "call" -> m.text
    else -> m.toolName
}
