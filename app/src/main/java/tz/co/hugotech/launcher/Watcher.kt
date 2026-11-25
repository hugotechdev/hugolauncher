package tz.co.hugotech.launcher

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ------------------------------- Notifications ------------------------------- */

object Notify {
    private const val CHANNEL = "hugo_assistant"

    private fun ensure(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "HUGO Assistant", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        ensure(ctx)
        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // permission removed
        }
    }
}

/* --------------------------------- Reminders --------------------------------- */

object Reminders {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("hugo", Context.MODE_PRIVATE)

    private fun load(ctx: Context): MutableList<Pair<Long, String>> = try {
        val a = JSONArray(sp(ctx).getString("reminders", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getLong("at") to o.getString("text") } }.toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun save(ctx: Context, list: List<Pair<Long, String>>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("at", it.first).put("text", it.second)) }
        sp(ctx).edit().putString("reminders", a.toString()).apply()
    }

    fun pending(ctx: Context): List<Pair<Long, String>> = load(ctx).filter { it.first > System.currentTimeMillis() }.sortedBy { it.first }

    fun add(ctx: Context, at: Long, text: String) {
        save(ctx, pending(ctx) + (at to text))
        schedule(ctx, at, text)
    }

    private fun schedule(ctx: Context, at: Long, text: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("text", text).putExtra("at", at)
        val pi = PendingIntent.getBroadcast(ctx, (at / 1000).toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Alarms are lost when the phone restarts; set them again when the launcher starts. */
    fun reschedule(ctx: Context) {
        pending(ctx).forEach { schedule(ctx, it.first, it.second) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        val at = intent.getLongExtra("at", System.currentTimeMillis())
        Notify.show(context, (at / 1000).toInt(), "HUGO", text)
    }
}

/* ------------------------------ Gentle nudges ------------------------------ */

/**
 * Every 15 minutes: looks at screen time and unfinished tasks and, at most
 * three times a day, sends a short nudge. Quiet at night and in Worship.
 */
class WatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val sp = ctx.getSharedPreferences("hugo", Context.MODE_PRIVATE)
        if (!sp.getBoolean("assistant_on", true) || !sp.getBoolean("ai_nudges", true)) return Result.success()
        if (System.currentTimeMillis() < sp.getLong("assistant_paused_until", 0L)) return Result.success()
        if (sp.getString("phone_mode", "work") == "worship" && !sp.getBoolean("auto_mode", false)) return Result.success()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return Result.success()

        val sw = sp.getString("lang", "en") == "sw"
        fun t(en: String, s: String) = if (sw) s else en
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
        if (sp.getString("nudge_day", "") != today) {
            sp.edit().putString("nudge_day", today).putInt("nudge_count", 0).putStringSet("nudge_sent", emptySet()).apply()
        }
        if (sp.getInt("nudge_count", 0) >= 3) return Result.success()
        val sent = sp.getStringSet("nudge_sent", emptySet())?.toMutableSet() ?: mutableSetOf()

        var message: String? = null
        var tag = ""
        if (Usage.hasAccess(ctx)) {
            val usage = Usage.todayMinutes(ctx).filterKeys { it != ctx.packageName }
            val social = usage.filterKeys { isSocial(it) }.values.sum()
            val total = usage.values.sum()
            when {
                social >= 90 && "social90" !in sent -> {
                    tag = "social90"
                    message = t("You've spent $social minutes on social media today. Maybe a short break?", "Umetumia dakika $social kwenye mitandao ya kijamii leo. Pumzika kidogo?")
                }
                social >= 45 && "social45" !in sent -> {
                    tag = "social45"
                    message = t("$social minutes on social media so far today.", "Dakika $social kwenye mitandao ya kijamii mpaka sasa leo.")
                }
                total >= 240 && "total240" !in sent -> {
                    tag = "total240"
                    message = t("${total / 60} hours on the phone today. Rest your eyes for a few minutes.", "Saa ${total / 60} kwenye simu leo. Pumzisha macho dakika chache.")
                }
            }
        }
        if (message == null && hour >= 17 && "tasks" !in sent) {
            val left = try {
                val a = JSONArray(sp.getString("tasks", "[]") ?: "[]")
                (0 until a.length()).count { !a.getJSONObject(it).optBoolean("done") }
            } catch (e: Exception) {
                0
            }
            if (left > 0) {
                tag = "tasks"
                message = t("$left of today's tasks are still open.", "Kazi $left za leo bado hazijamalizika.")
            }
        }
        if (message != null) {
            Notify.show(ctx, 7000 + sent.size, "HUGO", message)
            sent.add(tag)
            sp.edit().putInt("nudge_count", sp.getInt("nudge_count", 0) + 1).putStringSet("nudge_sent", sent).apply()
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("hugo-watch", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
