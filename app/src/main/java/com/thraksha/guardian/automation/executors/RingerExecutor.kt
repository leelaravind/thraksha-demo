package com.thraksha.guardian.automation.executors

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.SnapshotEntry

/**
 * Ringer mode via AudioManager. NORMAL↔VIBRATE needs no special access, but Android
 * routes SILENT transitions through Do Not Disturb — so to guarantee both application
 * AND restoration of any possible previous mode (the user might be in SILENT when a
 * routine starts, §9), this executor requires Notification Policy Access up front and
 * is honest about why.
 */
class RingerExecutor : AutomationExecutor {

    override val capability = AutomationCapability.RINGER_MODE

    private fun audio(context: Context): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun support(context: Context): Pair<ActionSupport, String> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (nm.isNotificationPolicyAccessGranted) {
            ActionSupport.SUPPORTED to "ringer mode fully controllable"
        } else {
            ActionSupport.USER_ACTION_REQUIRED to
                "Silent-mode transitions (applying or restoring) require Do Not " +
                "Disturb access — grant it to Thraksha once"
        }
    }

    override fun snapshot(context: Context): SnapshotEntry {
        val mode = runCatching { audio(context).ringerMode }.getOrNull()
        return SnapshotEntry(
            capability = capability,
            previousValue = mode,
            readable = mode != null,
            detail = mode?.let { "previous ringer mode: ${modeName(it)}" }
                ?: "ringer mode unreadable",
        )
    }

    override fun apply(context: Context, target: ActionTarget): Boolean {
        val mode = target.intValue ?: return false
        audio(context).ringerMode = mode
        return true
    }

    override fun verify(context: Context, target: ActionTarget): Boolean =
        awaitState { audio(context).ringerMode == target.intValue }

    override fun restore(context: Context, entry: SnapshotEntry): Boolean {
        val previous = entry.previousValue ?: return false
        audio(context).ringerMode = previous
        return true
    }

    override fun verifyRestored(context: Context, entry: SnapshotEntry): Boolean =
        awaitState { audio(context).ringerMode == entry.previousValue }

    companion object {
        fun modeName(mode: Int): String = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
            else -> "UNKNOWN($mode)"
        }
    }
}
