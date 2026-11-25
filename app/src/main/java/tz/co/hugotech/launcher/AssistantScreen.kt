package tz.co.hugotech.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ask the confirm dialog and wait for the user's answer. */
suspend fun askConfirm(s: LauncherState, title: String, body: String): Boolean {
    val d = CompletableDeferred<Boolean>()
    s.pendingConfirm.value = Confirm(title, body, d)
    return d.await()
}

@Composable
fun AssistantScreen(initial: String, onClose: () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val chat = s.chat
    val provider = s.aiProvider.value
    val hasKey = remember(provider, Keys.version.value) { Keys.get(ctx, provider).isNotBlank() }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setLanguage(if (s.lang.value == "sw") Locale("sw", "TZ") else Locale.ENGLISH)
                tts.value = engine
            }
        }
        onDispose { engine?.shutdown() }
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || s.aiBusy.value) return
        input = ""
        scope.launch {
            Agent.ask(s, q) { title, body -> askConfirm(s, title, body) }
            val last = s.chat.lastOrNull()
            if (s.speakReplies.value && last != null && last.role == "assistant" && last.text.isNotBlank()) {
                tts.value?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "hugo")
            }
        }
    }

    LaunchedEffect(initial) { if (initial.isNotBlank()) send(initial) }
    LaunchedEffect(chat.size) { if (chat.isNotEmpty()) list.animateScrollToItem(chat.size) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }

    Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Assistant", "Msaidizi"), color = pal.text, fontSize = 24.sp, fontWeight = FontWeight.Light)
                    Text(
                        when {
                            !s.assistantOn.value -> tr("Off", "Imezimwa")
                            !s.assistantActive() -> s.tr("Paused until ", "Imesitishwa hadi ") + SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(s.pausedUntil.value))
                            else -> Agent.providerName(provider)
                        },
                        color = pal.muted, fontSize = 12.sp
                    )
                }
                if (chat.isNotEmpty()) TextLink(tr("New chat", "Mazungumzo mapya")) { s.chat.clear() }
                Spacer(Modifier.width(6.dp))
                IconButtonSoft(G.SETTINGS) { nav.settings("ai") }
                Spacer(Modifier.width(6.dp))
                IconButtonSoft(G.DOWN) { onClose() }
            }

            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                if (!s.assistantActive()) {
                    item {
                        Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                            Text(tr("The assistant is resting.", "Msaidizi amepumzika."), color = pal.text, fontSize = 15.sp)
                            Text(tr("It isn't reading messages or sending reminders now.", "Hasomi ujumbe wala kutuma vikumbusho sasa hivi."), color = pal.muted, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Pill(tr("Turn on", "Mwashe")) {
                                s.assistantOn.value = true
                                s.pausedUntil.value = 0L
                            }
                        }
                    }
                } else if (!hasKey) {
                    item {
                        Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                            Text(tr("Connect an AI", "Unganisha AI"), color = pal.text, fontSize = 15.sp)
                            Text(
                                tr("Add a Claude, ChatGPT or Gemini API key. It stays encrypted on this phone.", "Weka API key ya Claude, ChatGPT au Gemini. Inahifadhiwa kwa usimbaji fiche kwenye simu hii."),
                                color = pal.muted, fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Pill(tr("Set up", "Weka")) { nav.settings("ai") }
                        }
                    }
                }
                if (chat.isEmpty()) {
                    item { TodayDigest() }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                s.tr("Who messaged me today?", "Nani amenitafuta leo?"),
                                s.tr("What's my schedule today?", "Ratiba yangu ya leo ni ipi?"),
                                s.tr("Remind me to drink water in 1 hour", "Nikumbushe kunywa maji baada ya saa 1"),
                                s.tr("How long have I used my phone today?", "Nimetumia simu muda gani leo?")
                            ).forEach { q ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, pal.border, RoundedCornerShape(16.dp))
                                        .clickable { send(q) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) { Text(q, color = pal.text, fontSize = 13.sp) }
                            }
                        }
                    }
                }
                items(chat.toList()) { m -> Bubble(m) }
                if (s.aiBusy.value) {
                    item { Text(tr("Working…", "Inafanya kazi…"), color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp)) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(pal.cardSolid)
                    .border(1.dp, pal.border, RoundedCornerShape(26.dp))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (input.isEmpty()) Text(tr("Ask or tell HUGO…", "Uliza au mwambie HUGO…"), color = pal.dim, fontSize = 15.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = pal.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(a.main),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RoundIcon(G.MIC, 42.dp, pal.bg.copy(alpha = 0f), pal.muted, fill = false) {
                    try {
                        voice.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, s.tr("Speak to HUGO", "Ongea na HUGO"))
                        )
                    } catch (e: Exception) {
                        s.toast.value = s.tr("Voice typing is not available", "Kuandika kwa sauti hakupo")
                    }
                }
                RoundIcon(G.UP, 42.dp, a.main, pal.bg, fill = false) { send(input) }
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    when (m.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.text,
                color = pal.text, fontSize = 15.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(a.main.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        "tool" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(a.main))
            Spacer(Modifier.width(8.dp))
            Text(toolLabel(s, m), color = pal.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        else -> if (m.text.isNotBlank()) {
            Text(
                m.text,
                color = pal.text, fontSize = 15.sp, lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/** What happened today, at a glance: messages, reminders, phone use. */
@Composable
fun TodayDigest() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val msgs = Inbox.items.value
    val reminders = remember { Reminders.pending(ctx) }
    val usage = remember { if (Usage.hasAccess(ctx)) Usage.todayMinutes(ctx).filterKeys { it != ctx.packageName } else emptyMap() }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Today so far", "Leo mpaka sasa"), color = pal.muted, fontSize = 12.sp)
        if (msgs.isNotEmpty()) {
            val bySender = msgs.groupBy { it.sender }.entries.sortedByDescending { it.value.size }.take(4)
            Text(s.tr("${msgs.size} messages", "Ujumbe ${msgs.size}") + " · " + bySender.joinToString(", ") { it.key + " (" + it.value.size + ")" }, color = pal.text, fontSize = 13.sp)
        } else {
            Text(
                if (s.music.hasAccess()) tr("No new messages yet.", "Bado hakuna ujumbe mpya.") else tr("Turn on notification access to see messages here.", "Washa ruhusa ya notifications kuona ujumbe hapa."),
                color = pal.dim, fontSize = 13.sp
            )
        }
        if (reminders.isNotEmpty()) {
            Text(
                s.tr("Next reminder: ", "Kikumbusho kijacho: ") + SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(reminders.first().first)) + " · " + reminders.first().second,
                color = pal.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (usage.isNotEmpty()) {
            val total = usage.values.sum()
            val social = usage.filterKeys { isSocial(it) }.values.sum()
            Text(s.tr("Phone ${total / 60}h ${total % 60}m · social ${social}m", "Simu saa ${total / 60} dk ${total % 60} · mitandao dk $social"), color = pal.text, fontSize = 13.sp)
        }
    }
}

/** Confirm card for anything the assistant wants to send. */
@Composable
fun ConfirmDialog() {
    val s = LocalLauncher.current
    val c = s.pendingConfirm.value ?: return
    fun answer(ok: Boolean) {
        s.pendingConfirm.value = null
        c.result.complete(ok)
    }
    AlertDialog(
        onDismissRequest = { answer(false) },
        title = { Text(c.title) },
        text = { Text(c.body, fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text(s.tr("Send", "Tuma")) } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text(s.tr("Cancel", "Ghairi")) } }
    )
}

/** On / pause row used in the panel. */
@Composable
fun AssistantSwitch() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val nav = LocalNav.current
    val active = s.assistantActive()
    Row(
        Modifier.fillMaxWidth().card().clickable { nav.assistant("") }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyph(G.SPARK, 18.dp, if (active) accent().main else pal.dim)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Assistant", "Msaidizi"), color = pal.text, fontSize = 15.sp)
            Text(
                when {
                    !s.assistantOn.value -> tr("Off", "Imezimwa")
                    !active -> s.tr("Paused until ", "Imesitishwa hadi ") + SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(s.pausedUntil.value))
                    else -> tr("On · tap to talk", "Imewashwa · gusa kuongea")
                },
                color = pal.dim, fontSize = 12.sp
            )
        }
        if (active) {
            TextLink(tr("Pause 1h", "Sitisha saa 1")) { s.pausedUntil.value = System.currentTimeMillis() + 3_600_000L }
        } else {
            TextLink(tr("Resume", "Endelea")) {
                s.assistantOn.value = true
                s.pausedUntil.value = 0L
            }
        }
    }
}

/** Settings → Assistant. */
@Composable
fun AssistantSettings() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val r = refresh

    Group(tr("Assistant", "Msaidizi")) {
        SettingToggle(tr("Assistant on", "Msaidizi amewashwa"), tr("Off means it reads nothing and sends nothing.", "Ukizima, hasomi wala hatumi chochote."), s.assistantOn.value) {
            s.assistantOn.value = it
            if (it) s.pausedUntil.value = 0L
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextLink(tr("Pause 1 hour", "Sitisha saa 1")) { s.pausedUntil.value = System.currentTimeMillis() + 3_600_000L }
            TextLink(tr("Pause till tomorrow", "Sitisha hadi kesho")) {
                val c = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 7); set(java.util.Calendar.MINUTE, 0)
                }
                s.pausedUntil.value = c.timeInMillis
            }
            if (s.pausedUntil.value > System.currentTimeMillis()) TextLink(tr("Resume", "Endelea")) { s.pausedUntil.value = 0L }
        }
    }

    Group(tr("AI provider", "Mtoa huduma wa AI")) {
        Segmented(listOf("claude" to "Claude", "openai" to "ChatGPT", "gemini" to "Gemini"), s.aiProvider.value) { s.aiProvider.value = it }
        KeyField("claude", "Claude API key", "console.anthropic.com", s.modelClaude)
        KeyField("openai", "ChatGPT (OpenAI) API key", "platform.openai.com", s.modelOpenai)
        KeyField("gemini", "Gemini API key", "aistudio.google.com", s.modelGemini)
        Text(
            tr("Keys are encrypted and never leave this phone except to the AI you choose.", "Key zinahifadhiwa kwa usimbaji fiche na hazitoki kwenye simu isipokuwa kwenda kwa AI uliyochagua."),
            color = pal.dim, fontSize = 12.sp
        )
    }

    Group(tr("What it may do", "Anachoruhusiwa")) {
        SettingToggle(
            tr("Read incoming messages", "Kusoma ujumbe unaoingia"),
            tr("From notifications (WhatsApp, SMS, Telegram, email). Kept for 24 hours on the phone.", "Kutoka notifications (WhatsApp, SMS, Telegram, email). Unakaa saa 24 kwenye simu."),
            s.readMessages.value
        ) {
            s.readMessages.value = it
            if (!it) Inbox.clear()
        }
        SettingToggle(
            tr("Gentle nudges", "Ushauri wa matumizi"),
            tr("Up to 3 a day: heavy social media, long phone time, open tasks. Quiet at night and in Worship.", "Hadi mara 3 kwa siku: mitandao mingi, simu muda mrefu, kazi hazijaisha. Kimya usiku na wakati wa Ibada."),
            s.nudgesOn.value
        ) { s.nudgesOn.value = it }
        SettingToggle(tr("Speak answers aloud", "Jibu kwa sauti"), null, s.speakReplies.value) { s.speakReplies.value = it }
        Text(tr("Every message it sends is shown to you first.", "Kila ujumbe anaotuma unaonyeshwa kwako kwanza."), color = pal.dim, fontSize = 12.sp)
    }

    androidx.compose.runtime.key(r) {
        Group(tr("Permissions", "Ruhusa")) {
            Permission(tr("Messages and music (notification access)", "Ujumbe na muziki (notification access)"), s.music.hasAccess()) { s.music.openAccessSettings() }
            Permission(tr("Send SMS", "Kutuma SMS"), has(Manifest.permission.SEND_SMS)) { askPerms.launch(arrayOf(Manifest.permission.SEND_SMS)) }
            Permission(tr("Find contacts", "Kutafuta watu"), has(Manifest.permission.READ_CONTACTS)) { askPerms.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }
            if (Build.VERSION.SDK_INT >= 33) {
                Permission(tr("Reminders and nudges (notifications)", "Vikumbusho na ushauri (notifications)"), has(Manifest.permission.POST_NOTIFICATIONS)) {
                    askPerms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            Permission(tr("Screen time", "Muda wa skrini"), Usage.hasAccess(ctx)) { Usage.openSettings(ctx) }
        }
    }

    Group(tr("Memory", "Kumbukumbu")) {
        val facts = s.aiMemory.value
        if (facts.isEmpty()) Text(tr("Say “remember that…” and it keeps it here.", "Sema “kumbuka kwamba…” na itahifadhi hapa."), color = pal.dim, fontSize = 12.sp)
        facts.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f, color = pal.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextLink(tr("Forget", "Sahau")) { s.aiMemory.value = s.aiMemory.value - f }
            }
        }
    }
}

@Composable
private fun KeyField(provider: String, label: String, site: String, model: StrPref) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val saved = remember(Keys.version.value) { Keys.get(ctx, provider) }
    var key by remember(saved) { mutableStateOf(saved) }
    var showModel by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            key, { key = it.trim() }, singleLine = true,
            label = { Text(label) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (key != saved) TextLink(s.tr("Save", "Hifadhi")) { Keys.set(ctx, provider, key) }
            if (saved.isNotEmpty()) TextLink(s.tr("Remove", "Ondoa")) { Keys.set(ctx, provider, ""); key = "" }
            TextLink(s.tr("Model", "Model")) { showModel = !showModel }
            Text(site, color = pal.dim, fontSize = 11.sp)
        }
        if (showModel) {
            var m by remember { mutableStateOf(model.value) }
            OutlinedTextField(m, { m = it.trim() }, singleLine = true, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            if (m != model.value && m.isNotBlank()) TextLink(s.tr("Save model", "Hifadhi model")) { model.value = m }
        }
    }
}

