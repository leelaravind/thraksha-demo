package com.thraksha.guardian.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Duration expiry via AlarmManager (guide §20): survives process death, unlike an
 * in-memory timer. Exact alarms are used when the OS permits
 * (`canScheduleExactAlarms`); otherwise the inexact while-idle variant is an honest,
 * documented fallback (minute-level accuracy is sufficient for routine expiry).
 * Recovery on app start ([AutomationEngine.recover]) backstops both paths.
 */
object AutomationScheduler {

    private const val TAG = "AutomationScheduler"
    private const val REQUEST_CODE = 4001

    const val ACTION_EXPIRE = "com.thraksha.guardian.automation.EXPIRE"

    fun schedule(context: Context, run: PersistedRun) {
        val expiresAt = run.expiresAt ?: return
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, expiresAt, pending,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, expiresAt, pending,
                )
            }
            Log.i(TAG, "Expiry scheduled (exact=$canExact) at $expiresAt for ${run.runId}")
        }.onFailure { Log.e(TAG, "Failed to schedule expiry", it) }
    }

    fun cancel(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarmManager.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, AutomationAlarmReceiver::class.java).setAction(ACTION_EXPIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Fires when an active routine's duration expires → STOP & RESTORE. */
class AutomationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationScheduler.ACTION_EXPIRE) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                AutomationEngine.stop(context, "routine duration expired")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
