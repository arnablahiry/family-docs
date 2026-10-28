package com.familydocs.app.events

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.familydocs.app.FamilyDocsApp
import com.familydocs.app.MainActivity
import com.familydocs.app.R
import com.familydocs.app.data.EventEntity
import com.familydocs.app.domain.Events
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Local reminders for events: one WorkManager job per event, at 9:00 on the chosen day
 * ("1 week before" → 9:00 seven days before it starts). Nothing leaves the phone.
 */
object EventReminders {
    private const val CHANNEL = "event_reminders"
    private fun name(id: String) = "event-reminder-$id"

    fun schedule(context: Context, e: EventEntity) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(name(e.id))
        if (e.remindDays < 0) return
        val start = Events.parse(e.startDay) ?: return
        val at = start.minusDays(e.remindDays.toLong()).atTime(9, 0)
        val delay = Duration.between(LocalDateTime.now(), at).toMillis()
        if (delay <= 0) return
        val req = OneTimeWorkRequestBuilder<Worker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to e.id)).build()
        wm.enqueueUniqueWork(name(e.id), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context, id: String) { WorkManager.getInstance(context).cancelUniqueWork(name(id)) }

    /** After a restore, so every event's reminder is set again on this phone. */
    fun rescheduleAll(context: Context, events: List<EventEntity>) = events.forEach { schedule(context, it) }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        @android.annotation.SuppressLint("MissingPermission") // checked by canNotify() just below
        override suspend fun doWork(): Result {
            val c = (applicationContext as FamilyDocsApp).container
            val e = inputData.getString("id")?.let { c.repo.eventNow(it) } ?: return Result.success()
            if (e.remindDays < 0 || !canNotify(applicationContext)) return Result.success()
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Trips, birthdays and appointments you asked to be reminded about"
                })
            }
            val open = PendingIntent.getActivity(
                applicationContext, e.id.hashCode(),
                Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Only the event's name and when: no document details on the lock screen.
            val n = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_event)
                .setContentTitle(e.name)
                .setContentText("${Events.countdown(e.startDay, e.endDay)} · ${Events.dates(e.startDay, e.endDay)}")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            try { NotificationManagerCompat.from(applicationContext).notify(e.id.hashCode(), n) } catch (_: SecurityException) { }
            return Result.success()
        }
    }
}
