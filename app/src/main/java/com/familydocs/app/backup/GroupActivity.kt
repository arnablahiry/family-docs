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

/**
 * Silent notifications when someone in a group changes things ("Mohua · Family: added Passport, updated Driving
 * licence"): no sound, no vibration, no pop-up; they just wait in the notification shade. On the lock screen only
 * "Lararium · Family has updates" shows. Turn them off in the phone's settings (Lararium › Notifications › Group activity).
 */
object GroupActivity {
    private const val CHANNEL = "group_activity"

    @android.annotation.SuppressLint("MissingPermission") // checked by canNotify()
    fun show(context: Context, groupId: String, groupName: String, who: String, what: List<String>) {
        if (what.isEmpty() || !EventReminders.canNotify(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Group activity", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quietly, when someone in your group adds, changes or removes documents"
                setSound(null, null); enableVibration(false); setShowBadge(true)
            })
        val id = "$groupId|$who".hashCode()
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = what.joinToString(", ").replaceFirstChar { it.uppercase() }
        val hidden = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle("Lararium").setContentText("$groupName has updates").build()
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle("$who · $groupName").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(hidden)
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { }
    }
}
