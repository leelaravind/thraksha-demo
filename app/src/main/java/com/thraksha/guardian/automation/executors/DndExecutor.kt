package com.thraksha.guardian.automation.executors

import android.app.NotificationManager
import android.content.Context
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.SnapshotEntry

/**
 * Do Not Disturb via the supported interruption-filter API. Requires the user-granted
 * Notification Policy Access (capability matrix §1) — without it the action is
 * USER_ACTION_REQUIRED with the exact settings deep link, never silently skipped.
 *
 * Only the interruption *filter* is changed. The user's priority-category
 * NotificationPolicy is deliberately untouched: modifying it would require snapshotting
 * a much larger structure to honour §9, for no demo value.
 */
class DndExecutor : AutomationExecutor {

    override val capability = AutomationCapability.DO_NOT_DISTURB

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun support(context: Context): Pair<ActionSupport, String> =
        if (manager(context).isNotificationPolicyAccessGranted) {
            ActionSupport.SUPPORTED to "Notification Policy Access granted"
        } else {
            ActionSupport.USER_ACTION_REQUIRED to
                "Grant Do Not Disturb access to Thraksha (one-time, revocable in the " +
                "same screen)"
        }

    override fun snapshot(context: Context): SnapshotEntry {
        val filter = runCatching { manager(context).currentInterruptionFilter }.getOrNull()
        val readable = filter != null &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        return SnapshotEntry(
            capability = capability,
            previousValue = filter.takeIf { readable },
            readable = readable,
            detail = if (readable) {
                "previous interruption filter: ${filterName(filter!!)}"
            } else {
                "interruption filter unreadable"
            },
        )
    }

    override fun apply(context: Context, target: ActionTarget): Boolean {
        val filter = target.intValue ?: return false
        manager(context).setInterruptionFilter(filter)
        return true
    }

    override fun verify(context: Context, target: ActionTarget): Boolean =
        awaitState { manager(context).currentInterruptionFilter == target.intValue }

    override fun restore(context: Context, entry: SnapshotEntry): Boolean {
        val previous = entry.previousValue ?: return false
        manager(context).setInterruptionFilter(previous)
        return true
    }

    override fun verifyRestored(context: Context, entry: SnapshotEntry): Boolean =
        awaitState { manager(context).currentInterruptionFilter == entry.previousValue }

    companion object {
        fun filterName(filter: Int): String = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "ALL (DND off)"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY (DND on)"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "NONE (total silence)"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS ONLY"
            else -> "UNKNOWN($filter)"
        }
    }
}
