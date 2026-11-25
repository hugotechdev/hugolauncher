package tz.co.hugotech.launcher

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.CalendarContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

/* ------------------------- Notifications + music ------------------------- */

object NotificationState {
    val counts = mutableStateOf<Map<String, Int>>(emptyMap())
}

class MusicListenerService : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
        if (sbn != null) {
            try {
                Inbox.capture(this, sbn)
            } catch (e: Exception) {
                // never break the listener
            }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val list = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val map = HashMap<String, Int>()
        for (n in list) {
            val f = n.notification.flags
            if (f and Notification.FLAG_GROUP_SUMMARY != 0 || f and Notification.FLAG_ONGOING_EVENT != 0) continue
            map[n.packageName] = (map[n.packageName] ?: 0) + 1
        }
        NotificationState.counts.value = map
    }
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val pkg: String,
    val position: Long,
    val updatedAt: Long,
    val speed: Float,
    val duration: Long
) {
    fun currentPosition(): Long {
        if (!playing) return position
        val p = position + ((SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
        return if (duration > 0) p.coerceIn(0, duration) else p
    }
}

class MusicController(private val context: Context) {
    val state = mutableStateOf<NowPlaying?>(null)
    private val msm: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MusicListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = attach(null)
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }
    private val sleepRunnable = Runnable { controller?.transportControls?.pause() }

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start() {
        val m = msm ?: return
        if (!hasAccess()) {
            state.value = null
            return
        }
        try {
            if (!listening) {
                m.addOnActiveSessionsChangedListener(sessionsListener, component)
                listening = true
            }
            attach(null)
        } catch (e: SecurityException) {
            state.value = null
        }
    }

    fun stop() {
        try {
            if (listening) msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // ignore
        }
        listening = false
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun attach(list: List<MediaController>?) {
        val sessions = list ?: try {
            msm?.getActiveSessions(component) ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == AUDIOMACK }
            ?: sessions.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            pick?.registerCallback(callback, handler)
        }
        update()
    }

    fun update() {
        val c = controller
        if (c == null) {
            state.value = null
            return
        }
        val m = c.metadata
        val s = c.playbackState
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        state.value = NowPlaying(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown track",
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            art = art?.asImageBitmap(),
            playing = s?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName,
            position = s?.position ?: 0L,
            updatedAt = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = s?.playbackSpeed ?: 1f,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Pauses the music after [minutes]; 0 cancels. */
    fun sleepAfter(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes > 0) handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    fun openAccessSettings() {
        AppRepository.safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    companion object {
        const val AUDIOMACK = "com.audiomack"
    }
}

/* ----------------------------- Double-tap lock ----------------------------- */

class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        var instance: LockService? = null

        fun enabled(): Boolean = instance != null

        fun lock(): Boolean {
            if (Build.VERSION.SDK_INT < 28) return false
            return instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }

        fun openSettings(context: Context) {
            AppRepository.safeStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}

/* --------------------------------- Weather --------------------------------- */

data class DayWeather(val day: String, val code: Int, val max: Int, val min: Int)
data class Weather(val city: String, val temp: Int, val code: Int, val days: List<DayWeather>, val at: Long)

object WeatherService {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            c.disconnect()
        }
    }

    fun cached(s: LauncherState): Weather? = try {
        parse(JSONObject(s.sp.getString("weather_json", "") ?: ""))
    } catch (e: Exception) {
        null
    }

    private fun parse(o: JSONObject): Weather {
        val a = o.getJSONArray("days")
        return Weather(
            o.getString("city"), o.getInt("temp"), o.getInt("code"),
            (0 until a.length()).map {
                val d = a.getJSONObject(it)
                DayWeather(d.getString("day"), d.getInt("code"), d.getInt("max"), d.getInt("min"))
            },
            o.getLong("at")
        )
    }

    /** Runs on a background thread. Uses Open-Meteo (free, no key). */
    fun fetch(s: LauncherState): Weather? = try {
        val city = s.weatherCity.value.ifBlank { "Dar es Salaam" }
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8")))
        val place = geo.getJSONArray("results").getJSONObject(0)
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val f = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=4"
            )
        )
        val cur = f.getJSONObject("current")
        val daily = f.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val days = JSONArray()
        for (i in 0 until times.length()) {
            val parts = times.getString(i).split("-").map { it.toInt() }
            val c = Calendar.getInstance().apply { set(parts[0], parts[1] - 1, parts[2]) }
            days.put(
                JSONObject()
                    .put("day", names[c.get(Calendar.DAY_OF_WEEK) - 1])
                    .put("code", daily.getJSONArray("weather_code").getInt(i))
                    .put("max", Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i)).toInt())
                    .put("min", Math.round(daily.getJSONArray("temperature_2m_min").getDouble(i)).toInt())
            )
        }
        val o = JSONObject()
            .put("city", place.optString("name", city))
            .put("temp", Math.round(cur.getDouble("temperature_2m")).toInt())
            .put("code", cur.getInt("weather_code"))
            .put("days", days)
            .put("at", System.currentTimeMillis())
        s.sp.edit().putString("weather_json", o.toString()).apply()
        parse(o)
    } catch (e: Exception) {
        null
    }

    fun label(code: Int, s: LauncherState): String = when (code) {
        0 -> s.tr("Clear", "Jua")
        1, 2 -> s.tr("Partly cloudy", "Mawingu kiasi")
        3 -> s.tr("Cloudy", "Mawingu")
        45, 48 -> s.tr("Fog", "Ukungu")
        in 51..67, in 80..82 -> s.tr("Rain", "Mvua")
        in 95..99 -> s.tr("Storm", "Radi")
        else -> s.tr("Cloudy", "Mawingu")
    }

    fun icon(code: Int): String = when (code) {
        0 -> G.SUN
        1, 2, 3, 45, 48 -> G.CLOUD
        in 95..99 -> G.STORM
        else -> G.RAIN
    }
}

/* -------------------------------- Calendar -------------------------------- */

data class CalEvent(val title: String, val begin: Long, val allDay: Boolean)

object CalendarData {
    fun hasAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun query(context: Context, from: Long, to: Long): List<CalEvent> {
        if (!hasAccess(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from)
            android.content.ContentUris.appendId(it, to)
        }.build()
        val out = ArrayList<CalEvent>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext()) out.add(CalEvent(c.getString(0) ?: "", c.getLong(1), c.getInt(2) == 1))
            }
        } catch (e: Exception) {
            // calendar not readable
        }
        return out
    }

    fun today(context: Context): List<CalEvent> {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        return query(context, start.timeInMillis, start.timeInMillis + 86_400_000L)
    }

    /** Days of the current month that have at least one event. */
    fun busyDaysThisMonth(context: Context): Set<Int> {
        val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return query(context, start.timeInMillis, end.timeInMillis).map {
            Calendar.getInstance().apply { timeInMillis = it.begin }.get(Calendar.DAY_OF_MONTH)
        }.toSet()
    }

    fun openCalendar(context: Context) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        AppRepository.safeStart(context, Intent(Intent.ACTION_VIEW, uri))
    }
}

/* ------------------------------ Screen time ------------------------------ */

object Usage {
    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Minutes each app was in front today. */
    fun todayMinutes(context: Context): Map<String, Int> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        return try {
            usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
                .mapValues { (it.value.totalTimeInForeground / 60_000L).toInt() }
                .filterValues { it > 0 }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun openSettings(context: Context) {
        AppRepository.safeStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}

/* ----------------------------- Phone controls ----------------------------- */

object Controls {
    private var torchOn = false

    fun torch(context: Context): Boolean {
        val cm = context.getSystemService(CameraManager::class.java) ?: return false
        return try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchOn
        } catch (e: Exception) {
            false
        }
    }

    fun dndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun dndOn(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY

    fun setDnd(context: Context, on: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            AppRepository.safeStart(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun brightness(context: Context): Float = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }

    fun setBrightness(context: Context, v: Float) {
        if (!Settings.System.canWrite(context)) {
            AppRepository.safeStart(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
            return
        }
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(5, 255))
        } catch (e: Exception) {
            // not allowed
        }
    }

    fun volume(context: Context): Float {
        val am = context.getSystemService(AudioManager::class.java) ?: return 0.5f
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun setVolume(context: Context, v: Float) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0)
    }

    fun wifiPanel(context: Context) {
        val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
        AppRepository.safeStart(context, i)
    }

    fun bluetoothSettings(context: Context) {
        AppRepository.safeStart(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun dial(context: Context, number: String) {
        AppRepository.safeStart(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }
}

/* ------------------------------ Backup data ------------------------------ */

object Backup {
    fun export(s: LauncherState): String {
        val o = JSONObject()
        s.sp.all.forEach { (k, v) ->
            when (v) {
                is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
                is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is Set<*> -> o.put(k, JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() })))
            }
        }
        return JSONObject().put("hugo_backup", 1).put("prefs", o).toString(2)
    }

    fun import(s: LauncherState, text: String): Boolean = try {
        val prefs = JSONObject(text).getJSONObject("prefs")
        val e = s.sp.edit().clear()
        prefs.keys().forEach { k ->
            val item = prefs.getJSONObject(k)
            when (item.getString("t")) {
                "s" -> e.putString(k, item.getString("v"))
                "b" -> e.putBoolean(k, item.getBoolean("v"))
                "i" -> e.putInt(k, item.getInt("v"))
                "l" -> e.putLong(k, item.getLong("v"))
                "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                "set" -> {
                    val a = item.getJSONArray("v")
                    e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        e.commit()
        true
    } catch (e: Exception) {
        false
    }
}
