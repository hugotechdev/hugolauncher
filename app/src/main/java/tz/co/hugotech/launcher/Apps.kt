package tz.co.hugotech.launcher

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Xml
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream

object AppRepository {

    /** Last known app list, so the home screen appears instantly on start. */
    fun cached(s: LauncherState): List<AppInfo> = try {
        val a = JSONArray(s.sp.getString("app_cache", "[]") ?: "[]")
        val renamed = s.renamed.value
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val pkg = o.getString("p")
            val act = o.getString("a")
            val real = o.getString("l")
            AppInfo(renamed["$pkg/$act"] ?: real, real, pkg, act)
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Reads installed apps (labels only — icons load later, one by one). */
    @Suppress("DEPRECATION")
    fun load(s: LauncherState): List<AppInfo> {
        val ctx = s.context
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val renamed = s.renamed.value
        val list = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                val real = it.loadLabel(pm).toString()
                val key = it.activityInfo.packageName + "/" + it.activityInfo.name
                AppInfo(renamed[key] ?: real, real, it.activityInfo.packageName, it.activityInfo.name)
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("p", it.pkg).put("a", it.activity).put("l", it.realLabel)) }
        s.sp.edit().putString("app_cache", a.toString()).apply()
        return list
    }

    fun safeStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Can't open this", Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        Toast.makeText(context, "Can't open this", Toast.LENGTH_SHORT).show()
        false
    }

    fun openPackage(context: Context, pkg: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return safeStart(context, i)
    }

    fun openInfo(context: Context, pkg: String) {
        safeStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }

    @Suppress("DEPRECATION")
    fun uninstall(context: Context, pkg: String) {
        safeStart(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    private fun startApp(context: Context, app: AppInfo) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.pkg, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(context, i)
    }

    /** Opens an app: asks for fingerprint if locked, warns if over its daily limit. */
    fun open(s: LauncherState, app: AppInfo, skipLimit: Boolean = false) {
        if (app.key in s.locked.value) {
            unlock(s, app.label) { openChecked(s, app, skipLimit) }
        } else {
            openChecked(s, app, skipLimit)
        }
    }

    private fun openChecked(s: LauncherState, app: AppInfo, skipLimit: Boolean) {
        val limit = s.limits.value[app.pkg]?.toIntOrNull()
        if (!skipLimit && limit != null && Usage.hasAccess(s.context)) {
            val used = Usage.todayMinutes(s.context)[app.pkg] ?: 0
            if (used >= limit) {
                s.pendingLimit.value = app
                return
            }
        }
        s.recordLaunch(app.pkg)
        startApp(s.context, app)
    }

    fun unlock(s: LauncherState, label: String, onOk: () -> Unit) {
        val act = s.activity
        val can = BiometricManager.from(act).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(act, s.tr("Fingerprint is not set up on this phone", "Alama ya kidole haijawekwa kwenye simu hii"), Toast.LENGTH_LONG).show()
            onOk()
            return
        }
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(s.tr("Unlock $label", "Fungua $label"))
            .setNegativeButtonText(s.tr("Cancel", "Ghairi"))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun defaultPackage(context: Context, intent: Intent): String? {
        @Suppress("DEPRECATION")
        val ri = context.packageManager.resolveActivity(intent, 0) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return if (pkg == "android") null else pkg
    }

    /** First Home screen: most-used apps, else common ones, never the dock apps. */
    fun starterHome(context: Context, s: LauncherState): List<String> {
        val apps = s.visibleApps()
        val dockPkgs = s.dock.value.mapNotNull { k -> apps.firstOrNull { it.key == k }?.pkg }.toSet() +
            defaultDockPackages(context).toSet()
        val used = try {
            Usage.todayMinutes(context).entries.sortedByDescending { it.value }.map { it.key }
        } catch (e: Throwable) { emptyList() }
        val common = listOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.google.android.youtube", "com.instagram.android",
            "com.facebook.katana", "com.google.android.gm", "com.android.chrome", "com.google.android.apps.maps",
            "com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.vending",
            "com.google.android.calendar", "com.samsung.android.calendar", "com.android.settings",
            "tz.co.hugotech.mix", "com.audiomack", "com.spotify.music"
        )
        val out = ArrayList<String>()
        for (p in used + common) {
            if (out.size >= 8) break
            if (p in dockPkgs) continue
            apps.firstOrNull { it.pkg == p }?.let { if (it.key !in out) out += it.key }
        }
        for (a in apps) {
            if (out.size >= 8) break
            if (a.pkg !in dockPkgs && a.key !in out) out += a.key
        }
        return out
    }

    /** Phone, messages, camera, browser — the phone's own defaults. */
    fun defaultDockPackages(context: Context): List<String> = listOfNotNull(
        defaultPackage(context, Intent(Intent.ACTION_DIAL)),
        defaultPackage(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
        defaultPackage(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        defaultPackage(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
    ).distinct()

    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        try {
            val sb = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Throwable) {
            // not available on this phone
        }
    }
}

/* ------------------------------- Icon packs ------------------------------- */

data class IconPackInfo(val pkg: String, val label: String)

object IconPacks {
    private val maps = HashMap<String, Map<String, String>>()

    @Suppress("DEPRECATION")
    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
        return actions.flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun map(context: Context, pack: String): Map<String, String> = maps.getOrPut(pack) {
        val out = HashMap<String, String>()
        try {
            val res = context.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (id != 0) {
                res.getXml(id)
            } else {
                Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val draw = parser.getAttributeValue(null, "drawable")
                    if (comp != null && draw != null) out[comp] = draw
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            // pack without a readable appfilter
        }
        out
    }

    fun drawable(context: Context, pack: String, app: AppInfo): Drawable? {
        val name = map(context, pack)["ComponentInfo{${app.pkg}/${app.activity}}"] ?: return null
        return try {
            val res: Resources = context.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier(name, "drawable", pack)
            if (id == 0) null else res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() = maps.clear()
}

/* ------------------------------- Icon cache ------------------------------- */

class AppIcons(val full: ImageBitmap?, val mono: ImageBitmap?)

object IconCache {
    private const val SIZE = 144
    // About 24 MB of icons at most
    private val cache = object : LruCache<String, AppIcons>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppIcons): Int =
            (value.full?.let { it.width * it.height * 4 } ?: 0) + (value.mono?.let { it.width * it.height * 4 } ?: 0) + 64
    }

    fun peek(key: String): AppIcons? = cache.get(key)

    fun clear() = cache.evictAll()

    fun load(s: LauncherState, app: AppInfo): AppIcons {
        cache.get(app.key)?.let { return it }
        val ctx = s.context
        val custom = s.customIcons.value[app.key]
        val result = try {
            when {
                custom != null && File(custom).exists() -> {
                    AppIcons(BitmapFactory.decodeFile(custom)?.asImageBitmap(), null)
                }
                s.iconPack.value.isNotEmpty() && IconPacks.drawable(ctx, s.iconPack.value, app) != null -> {
                    val d = IconPacks.drawable(ctx, s.iconPack.value, app)!!
                    AppIcons(d.toBitmap(SIZE, SIZE).asImageBitmap(), null)
                }
                else -> {
                    val d = ctx.packageManager.getActivityIcon(ComponentName(app.pkg, app.activity))
                    val needMono = s.iconTheme.value == "mono" || s.uniformIcons.value
                    AppIcons(d.toBitmap(SIZE, SIZE).asImageBitmap(), if (needMono) monochrome(d) else null)
                }
            }
        } catch (e: Exception) {
            AppIcons(null, null)
        }
        cache.put(app.key, result)
        return result
    }

    private fun monochrome(d: Drawable): ImageBitmap? {
        if (Build.VERSION.SDK_INT < 33 || d !is AdaptiveIconDrawable) return null
        val m = d.monochrome ?: return null
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pad = (SIZE * 0.25f).toInt()
        m.setBounds(-pad, -pad, SIZE + pad, SIZE + pad)
        m.draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }

    /** Saves a picture from the gallery as an app's own icon. */
    fun saveCustom(s: LauncherState, app: AppInfo, uri: Uri): Boolean = try {
        val input = s.context.contentResolver.openInputStream(uri) ?: return false
        val src = input.use { BitmapFactory.decodeStream(it) } ?: return false
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 192, 192, true)
        val dir = File(s.context.filesDir, "icons").apply { mkdirs() }
        val file = File(dir, app.key.hashCode().toString() + ".png")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        s.customIcons.put(app.key, file.absolutePath)
        cache.remove(app.key)
        s.iconVersion.intValue++
        true
    } catch (e: Exception) {
        false
    }

    fun resetCustom(s: LauncherState, app: AppInfo) {
        s.customIcons.value[app.key]?.let { File(it).delete() }
        s.customIcons.put(app.key, null)
        cache.remove(app.key)
        s.iconVersion.intValue++
    }
}

/** Loads an app's icon off the main thread and keeps it in memory. */
@Composable
fun rememberAppIcons(app: AppInfo): AppIcons? {
    val s = LocalLauncher.current
    val version = s.iconVersion.intValue
    val icons by produceState(initialValue = IconCache.peek(app.key), app.key, version) {
        if (value == null || version > 0) {
            value = withContext(Dispatchers.IO) { IconCache.load(s, app) }
        }
    }
    return icons
}
