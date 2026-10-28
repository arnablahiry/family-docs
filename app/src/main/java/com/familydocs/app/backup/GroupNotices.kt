package com.familydocs.app.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familydocs.app.MainActivity
import com.familydocs.app.R
import com.familydocs.app.events.EventReminders

/** Notifications about groups: someone asked to join, you were let in. Only names, never document details. */
object GroupNotices {
    private const val CHANNEL = "group_requests"

    @android.annotation.SuppressLint("MissingPermission") // checked by canNotify()
    fun show(context: Context, title: String, text: String, id: Int) {
        if (!EventReminders.canNotify(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Group requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When someone asks to join one of your groups, or a group lets you in"
            })
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_event).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { }
    }
}
