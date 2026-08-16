package com.thraksha.guardian.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.thraksha.guardian.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automation notifications (guide §41): one ongoing card while a routine is active,
 * one completion card when it restores. Calm product language, no spam.
 */
object AutomationNotifier {

    private const val TAG = "AutomationNotifier"
    private const val CHANNEL = "thraksha_automation"
    private const val ID_ACTIVE = 4101
    private const val ID_RESTORED = 4102

    fun showActive(context: Context, run: PersistedRun) {
        post(
            context, ID_ACTIVE,
            title = "${run.routineLabel} active",
            text = (run.expiresAt?.let {
                "Ends at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))}. "
            } ?: "") + "Tap to view or stop.",
            ongoing = true,
        )
    }

    fun showRestored(context: Context, run: PersistedRun, fullyRestored: Boolean) {
        NotificationManagerCompat.from(context).cancel(ID_ACTIVE)
        post(
            context, ID_RESTORED,
            title = if (fullyRestored) {
                "${run.routineLabel} ended"
            } else {
                "${run.routineLabel} ended — partial restore"
            },
            text = if (fullyRestored) {
                "Previous settings restored."
            } else {
                "Some settings could not be verified as restored — open Thraksha for details."
            },
            ongoing = false,
        )
    }

    private fun post(context: Context, id: Int, title: String, text: String, ongoing: Boolean) {
        runCatching {
            ensureChannel(context)
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            val tap = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationManagerCompat.from(context).notify(
                id,
                NotificationCompat.Builder(context, CHANNEL)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(tap)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Automation notification failed", it) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL, "Automation routines", NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Active routine status and restoration results."
                },
            )
    }
}
