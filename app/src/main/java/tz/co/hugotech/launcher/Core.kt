package tz.co.hugotech.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/* ----------------------------- Saved settings ----------------------------- */

class StrPref(private val sp: SharedPreferences, private val key: String, def: String) {
    private val s = mutableStateOf(sp.getString(key, def) ?: def)
    var value: String
        get() = s.value
        set(v) {
            s.value = v
            sp.edit().putString(key, v).apply()
        }
}

class BoolPref(private val sp: SharedPreferences, private val key: String, def: Boolean) {
    private val s = mutableStateOf(sp.getBoolean(key, def))
    var value: Boolean
        get() = s.value
        set(v) {
            s.value = v
            sp.edit().putBoolean(key, v).apply()
        }
}

class IntPref(private val sp: SharedPreferences, private val key: String, def: Int) {
    private val s = mutableIntStateOf(sp.getInt(key, def))
    var value: Int
        get() = s.intValue
        set(v) {
            s.intValue = v
            sp.edit().putInt(key, v).apply()
        }
}

class LongPref(private val sp: SharedPreferences, private val key: String, def: Long) {
    private val s = mutableLongStateOf(sp.getLong(key, def))
    var value: Long
        get() = s.longValue
        set(v) {
            s.longValue = v
            sp.edit().putLong(key, v).apply()
        }
}

class SetPref(private val sp: SharedPreferences, private val key: String) {
    private val s = mutableStateOf(sp.getStringSet(key, emptySet())?.toSet() ?: emptySet())
    var value: Set<String>
        get() = s.value
        set(v) {
            s.value = v
            sp.edit().putStringSet(key, HashSet(v)).apply()
        }

    fun toggle(item: String) {
        value = if (item in value) value - item else value + item
    }
}

/** A Map<String, String> saved as JSON (renamed apps, custom icons, time limits). */
class MapPref(private val sp: SharedPreferences, private val key: String) {
    private val s = mutableStateOf(read())
    private fun read(): Map<String, String> = try {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    var value: Map<String, String>
        get() = s.value
        set(v) {
            s.value = v
            sp.edit().putString(key, JSONObject(v).toString()).apply()
        }

    fun put(k: String, v: String?) {
        value = if (v == null) value - k else value + (k to v)
    }
}

/** A list of objects saved as a JSON array. */
class ListPref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    private val decode: (JSONObject) -> T,
    private val encode: (T) -> JSONObject
) {
    private val s = mutableStateOf(read())
    private fun read(): List<T> = try {
        val a = JSONArray(sp.getString(key, "[]") ?: "[]")
        (0 until a.length()).map { decode(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    var value: List<T>
        get() = s.value
        set(v) {
            s.value = v
            val a = JSONArray()
            v.forEach { a.put(encode(it)) }
            sp.edit().putString(key, a.toString()).apply()
        }
}

/** A list of plain strings saved in one preference (home items, dock). */
class StrListPref(private val sp: SharedPreferences, private val key: String) {
    private val s = mutableStateOf(read())
    private fun read(): List<String> = try {
        val a = JSONArray(sp.getString(key, "[]") ?: "[]")
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    var value: List<String>
        get() = s.value
        set(v) {
            s.value = v
            sp.edit().putString(key, JSONArray(v).toString()).apply()
        }
}

/* --------------------------------- Models --------------------------------- */

data class AppInfo(val label: String, val realLabel: String, val pkg: String, val activity: String) {
    val key: String get() = "$pkg/$activity"
}

data class Task(val title: String, val time: String, val done: Boolean)
data class Folder(val id: String, val name: String, val apps: List<String>)
data class Contact(val name: String, val number: String)
data class Journal(val date: String, val text: String)

/* ---------------------------------- State ---------------------------------- */

val LocalLauncher = staticCompositionLocalOf<LauncherState> { error("No launcher state") }

class LauncherState(val context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("hugo", Context.MODE_PRIVATE)
    lateinit var activity: FragmentActivity

    init {
        // v2.2: real app icons in their own colours by default
        if (sp.getInt("look_v", 0) < 22) sp.edit().putString("icon_theme", "original").putInt("look_v", 22).apply()
    }

    // Look
    val homeStyle = StrPref(sp, "home_style", "classic")      // classic | matte | mono
    val iconTheme = StrPref(sp, "icon_theme", "original")      // original | matte | gems | mono
    val homeSeeded = BoolPref(sp, "home_seeded", false)
    val accent = StrPref(sp, "accent", "gold")                 // gold | silver | rose | red
    val wallpaper = StrPref(sp, "wallpaper", "spiral")         // spiral | galaxy | atom | dawn | dna | cross | plain | custom
    val uniformIcons = BoolPref(sp, "uniform_icons", false)
    val showLabels = BoolPref(sp, "show_labels", true)
    val columns = IntPref(sp, "columns", 4)
    val iconPack = StrPref(sp, "icon_pack", "")
    val lang = StrPref(sp, "lang", "en")

    // Behaviour
    val doubleTapLock = BoolPref(sp, "double_tap_lock", true)
    val phoneMode = StrPref(sp, "phone_mode", "work")          // work | worship | rest
    val autoMode = BoolPref(sp, "auto_mode", false)
    val modeChangesLook = BoolPref(sp, "mode_look", true)
    val weatherCity = StrPref(sp, "weather_city", "Dar es Salaam")

    // Assistant
    val assistantOn = BoolPref(sp, "assistant_on", true)
    val pausedUntil = LongPref(sp, "assistant_paused_until", 0L)
    val aiProvider = StrPref(sp, "ai_provider", "claude")          // claude | openai | gemini
    val modelClaude = StrPref(sp, "model_claude", "claude-haiku-5-5")
    val modelOpenai = StrPref(sp, "model_openai", "gpt-4.1-mini")
    val modelGemini = StrPref(sp, "model_gemini", "gemini-2.5-flash")
    val readMessages = BoolPref(sp, "ai_read_messages", true)
    val nudgesOn = BoolPref(sp, "ai_nudges", true)
    val speakReplies = BoolPref(sp, "ai_speak", false)
    val aiMemory = StrListPref(sp, "ai_memory")

    // Apps
    val hidden = SetPref(sp, "hidden")
    val locked = SetPref(sp, "locked")
    val renamed = MapPref(sp, "renamed")
    val customIcons = MapPref(sp, "custom_icons")
    val limits = MapPref(sp, "limits")                         // pkg -> minutes per day
    val workHidden = SetPref(sp, "work_hidden")                // pkgs hidden in Work mode
    val home = StrListPref(sp, "home_items")                   // "app:<key>" or "folder:<id>"
    val dock = StrListPref(sp, "dock")                         // app keys
    val widgets = StrListPref(sp, "widgets")                   // widget ids
    val folders = ListPref(sp, "folders",
        { Folder(it.optString("id"), it.optString("name"), it.optJSONArray("apps")?.let { a -> (0 until a.length()).map { i -> a.getString(i) } } ?: emptyList()) },
        { JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps)) })

    // Work and faith
    val tasks = ListPref(sp, "tasks",
        { Task(it.optString("title"), it.optString("time"), it.optBoolean("done")) },
        { JSONObject().put("title", it.title).put("time", it.time).put("done", it.done) })
    val contacts = ListPref(sp, "contacts",
        { Contact(it.optString("name"), it.optString("number")) },
        { JSONObject().put("name", it.name).put("number", it.number) })
    val journal = ListPref(sp, "journal",
        { Journal(it.optString("date"), it.optString("text")) },
        { JSONObject().put("date", it.date).put("text", it.text) })
    val favVerses = SetPref(sp, "fav_verses")
    val planDay = IntPref(sp, "plan_day", 0)
    val streak = IntPref(sp, "streak", 0)
    val lastRead = StrPref(sp, "last_read", "")

    // Live values (not saved)
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    val iconVersion = mutableIntStateOf(0)
    val focusEnd = mutableLongStateOf(0L)
    val sleepEnd = mutableLongStateOf(0L)
    val pendingLimit = mutableStateOf<AppInfo?>(null)
    val toast = mutableStateOf<String?>(null)
    val chat = androidx.compose.runtime.mutableStateListOf<Msg>()
    val aiBusy = mutableStateOf(false)
    val pendingConfirm = mutableStateOf<Confirm?>(null)

    val music = MusicController(context)
    val widgetHost by lazy { WidgetHost(context) }

    init {
        if (!sp.contains("installed_at")) sp.edit().putLong("installed_at", System.currentTimeMillis()).apply()
        if (!sp.contains("work_hidden")) workHidden.value = emptySet()
        apps.value = AppRepository.cached(this)
    }

    val installedAt: Long get() = sp.getLong("installed_at", System.currentTimeMillis())

    fun tr(en: String, sw: String): String = if (lang.value == "sw") sw else en

    /** The assistant works only when it's on and not paused. */
    fun assistantActive(): Boolean = assistantOn.value && System.currentTimeMillis() >= pausedUntil.value

    fun lastLaunch(pkg: String): Long = sp.getLong("last_$pkg", 0L)
    fun launchCount(pkg: String): Int = sp.getInt("count_$pkg", 0)
    fun recordLaunch(pkg: String) {
        sp.edit().putLong("last_$pkg", System.currentTimeMillis()).putInt("count_$pkg", launchCount(pkg) + 1).apply()
    }

    /** Work / Worship / Rest — set by hand, or by the clock when auto is on. */
    fun effectiveMode(): String {
        if (!autoMode.value) return phoneMode.value
        val c = Calendar.getInstance()
        val day = c.get(Calendar.DAY_OF_WEEK)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        return when {
            day == Calendar.SUNDAY && hour in 6..12 -> "worship"
            day in Calendar.MONDAY..Calendar.FRIDAY && hour in 8..17 -> "work"
            day == Calendar.SATURDAY && hour in 9..12 -> "work"
            else -> "rest"
        }
    }

    /** Apps shown to the user: not hidden, and in Work mode no social apps. */
    fun visibleApps(): List<AppInfo> {
        val hiddenNow = hidden.value
        val work = effectiveMode() == "work"
        val workSet = workHidden.value
        return apps.value.filter { it.pkg !in hiddenNow && !(work && it.pkg in workSet) }
    }

    fun appByKey(key: String): AppInfo? = apps.value.firstOrNull { it.key == key }

    /** Wallpaper actually shown, following the phone mode when that option is on. */
    fun effectiveWallpaper(): String {
        if (!modeChangesLook.value) return wallpaper.value
        return when (effectiveMode()) {
            "worship" -> "cross"
            else -> wallpaper.value
        }
    }
}

val SOCIAL_HINTS = listOf(
    "facebook", "instagram", "tiktok", "musically", "zhiliaoapp", "twitter", "snapchat",
    "pinterest", "reddit", "threads", "likee", "kwai", "linkedin.android", "com.zhiliaoapp"
)

fun isSocial(pkg: String): Boolean = SOCIAL_HINTS.any { pkg.lowercase().contains(it) }
