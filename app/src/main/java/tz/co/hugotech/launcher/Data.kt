package tz.co.hugotech.launcher

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap,
    val mono: ImageBitmap?
)

data class Task(val title: String, val time: String, val done: Boolean)

/** Everything the launcher remembers, stored on the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hugo_launcher", Context.MODE_PRIVATE)

    init {
        if (!sp.contains("installed_at")) {
            sp.edit().putLong("installed_at", System.currentTimeMillis()).apply()
        }
    }

    val installedAt: Long get() = sp.getLong("installed_at", System.currentTimeMillis())

    var iconTheme: String
        get() = sp.getString("icon_theme", "matte") ?: "matte"
        set(v) { sp.edit().putString("icon_theme", v).apply() }

    var accent: String
        get() = sp.getString("accent", "gold") ?: "gold"
        set(v) { sp.edit().putString("accent", v).apply() }

    var showLabels: Boolean
        get() = sp.getBoolean("show_labels", true)
        set(v) { sp.edit().putBoolean("show_labels", v).apply() }

    fun loadTasks(): List<Task> {
        val raw = sp.getString("tasks", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Task(o.optString("title"), o.optString("time"), o.optBoolean("done"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTasks(list: List<Task>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("title", it.title).put("time", it.time).put("done", it.done))
        }
        sp.edit().putString("tasks", arr.toString()).apply()
    }

    fun dock(): List<String> =
        (sp.getString("dock", "") ?: "").split(",").filter { it.isNotBlank() }

    fun setDock(list: List<String>) {
        sp.edit().putString("dock", list.joinToString(",")).apply()
    }

    fun hidden(): Set<String> = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()

    fun setHidden(set: Set<String>) {
        sp.edit().putStringSet("hidden", HashSet(set)).apply()
    }

    fun recordLaunch(pkg: String) {
        sp.edit()
            .putLong("last_$pkg", System.currentTimeMillis())
            .putInt("count_$pkg", launchCount(pkg) + 1)
            .apply()
    }

    fun lastLaunch(pkg: String): Long = sp.getLong("last_$pkg", 0L)

    fun launchCount(pkg: String): Int = sp.getInt("count_$pkg", 0)

    fun favouriteVerses(): Set<String> = sp.getStringSet("fav_verses", emptySet())?.toSet() ?: emptySet()

    fun toggleFavourite(ref: String): Set<String> {
        val now = favouriteVerses().toMutableSet()
        if (!now.add(ref)) now.remove(ref)
        sp.edit().putStringSet("fav_verses", HashSet(now)).apply()
        return now
    }
}

object AppRepository {

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        return list
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { ri ->
                try {
                    val drawable = ri.loadIcon(pm)
                    AppInfo(
                        label = ri.loadLabel(pm).toString(),
                        pkg = ri.activityInfo.packageName,
                        activity = ri.activityInfo.name,
                        icon = drawable.toBitmap(144, 144).asImageBitmap(),
                        mono = monochrome(drawable)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .distinctBy { it.pkg + "/" + it.activity }
            .sortedBy { it.label.lowercase() }
    }

    /** Android 13+ "themed icon" layer, if the app provides one. */
    private fun monochrome(d: Drawable): ImageBitmap? {
        if (Build.VERSION.SDK_INT < 33 || d !is AdaptiveIconDrawable) return null
        val m = d.monochrome ?: return null
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = (size * 0.25f).toInt()
        m.setBounds(-pad, -pad, size + pad, size + pad)
        m.draw(c)
        return bmp.asImageBitmap()
    }

    fun launch(context: Context, app: AppInfo) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.pkg, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(context, i)
    }

    fun openPackage(context: Context, pkg: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return safeStart(context, i)
    }

    fun openInfo(context: Context, pkg: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, i)
    }

    @Suppress("DEPRECATION")
    fun uninstall(context: Context, pkg: String) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, i)
    }

    fun safeStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Can't open this", Toast.LENGTH_SHORT).show()
            false
        } catch (e: SecurityException) {
            Toast.makeText(context, "Can't open this", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun defaultPackage(context: Context, intent: Intent): String? {
        val ri = context.packageManager.resolveActivity(intent, 0) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return if (pkg == "android") null else pkg
    }

    /** Phone, messages, camera, browser — the phone's own default apps. */
    fun defaultDock(context: Context): List<String> = listOfNotNull(
        defaultPackage(context, Intent(Intent.ACTION_DIAL)),
        defaultPackage(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
        defaultPackage(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        defaultPackage(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
    ).distinct()

    /** Pulls down the notification shade (works on most phones). */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        try {
            val sb = context.getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Throwable) {
            // not supported on this phone
        }
    }
}

data class Verse(val text: String, val ref: String)

/** Short encouraging verses in Swahili (plain-language renderings). */
object Verses {
    val list = listOf(
        Verse("Usiogope, kwa maana mimi niko pamoja nawe.", "Isaya 41:10"),
        Verse("Ninaweza kufanya yote kupitia Kristo anayenipa nguvu.", "Wafilipi 4:13"),
        Verse("Uwe hodari na jasiri; Bwana Mungu wako yu pamoja nawe kila uendako.", "Yoshua 1:9"),
        Verse("Bwana ndiye mchungaji wangu; sitapungukiwa na kitu.", "Zaburi 23:1"),
        Verse("Mtegemee Bwana kwa moyo wako wote, wala usitegemee akili zako mwenyewe.", "Mithali 3:5"),
        Verse("Njooni kwangu nyote mlio wachovu na wenye mizigo, nami nitawapa raha.", "Mathayo 11:28"),
        Verse("Ninayajua mawazo ninayowawazia: mawazo ya amani, ya kuwapa tumaini na mwisho mwema.", "Yeremia 29:11"),
        Verse("Mambo yote hufanya kazi pamoja kwa faida ya wale wampendao Mungu.", "Warumi 8:28"),
        Verse("Mungu ni kimbilio letu na nguvu yetu, msaada ulio karibu wakati wa shida.", "Zaburi 46:1"),
        Verse("Wanaomngoja Bwana watapata nguvu mpya; watapaa juu kama tai.", "Isaya 40:31"),
        Verse("Mtwikeni yeye fadhaa zenu zote, kwa sababu anawajali.", "1 Petro 5:7"),
        Verse("Hii ndiyo siku aliyoifanya Bwana; tufurahi na kushangilia ndani yake.", "Zaburi 118:24"),
        Verse("Lolote mfanyalo, lifanyeni kwa moyo wote kama kwa Bwana.", "Wakolosai 3:23"),
        Verse("Mkabidhi Bwana kazi zako, na mipango yako itafanikiwa.", "Mithali 16:3"),
        Verse("Amani nawaachieni; amani yangu nawapa. Msifadhaike mioyo yenu.", "Yohana 14:27"),
        Verse("Msaada wangu unatoka kwa Bwana, aliyeziumba mbingu na nchi.", "Zaburi 121:2"),
        Verse("Mungu hakutupa roho ya woga, bali ya nguvu, upendo na kiasi.", "2 Timotheo 1:7"),
        Verse("Rehema za Bwana hazikomi; ni mpya kila asubuhi.", "Maombolezo 3:22-23"),
        Verse("Mkabidhi Bwana njia yako, umtumaini, naye atatenda.", "Zaburi 37:5"),
        Verse("Utamlinda katika amani kamili yule ambaye moyo wake umekutegemea.", "Isaya 26:3"),
        Verse("Tusichoke kutenda mema; kwa wakati wake tutavuna.", "Wagalatia 6:9"),
        Verse("Bwana ni nuru yangu na wokovu wangu; nimwogope nani?", "Zaburi 27:1"),
        Verse("Msiisumbukie kesho; kila siku ina mambo yake ya kutosha.", "Mathayo 6:34"),
        Verse("Sitakuacha kamwe, wala sitakupungukia.", "Waebrania 13:5"),
        Verse("Onjeni mwone ya kuwa Bwana ni mwema.", "Zaburi 34:8"),
        Verse("Mimi ni nuru ya ulimwengu; anifuataye hatatembea gizani.", "Yohana 8:12"),
        Verse("Furahini katika tumaini, vumilieni katika dhiki, dumuni katika maombi.", "Warumi 12:12"),
        Verse("Mbingu zinatangaza utukufu wa Mungu.", "Zaburi 19:1"),
        Verse("Tenda haki, penda rehema, na tembea kwa unyenyekevu na Mungu wako.", "Mika 6:8"),
        Verse("Bwana mwenyewe anakutangulia; hatakuacha. Usiogope.", "Kumbukumbu 31:8"),
        Verse("Upitapo katika maji mengi, mimi nitakuwa pamoja nawe.", "Isaya 43:2")
    )
}
