package tz.co.hugotech.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

/** Notification counts per app, used for the red badges. */
object NotificationState {
    val counts = mutableStateOf<Map<String, Int>>(emptyMap())
}

/**
 * Android only lets a launcher see music sessions and notifications through a
 * notification listener. The user turns it on once in Settings.
 */
class MusicListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        recount()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recount()
    }

    private fun recount() {
        val list = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val map = HashMap<String, Int>()
        for (n in list) {
            val flags = n.notification.flags
            if (flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (flags and Notification.FLAG_ONGOING_EVENT != 0) continue
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

    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MusicListenerService::class.java)
    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = attach(null)
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list)
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start() {
        if (!hasAccess() || msm == null) {
            state.value = null
            return
        }
        try {
            if (!listening) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, component)
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
            pick?.registerCallback(callback, Handler(Looper.getMainLooper()))
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
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
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
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun openAccessSettings() {
        AppRepository.safeStart(
            context,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        const val AUDIOMACK = "com.audiomack"
    }
}
