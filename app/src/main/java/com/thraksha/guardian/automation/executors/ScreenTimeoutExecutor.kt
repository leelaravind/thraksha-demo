package com.thraksha.guardian.automation.executors

import android.content.Context
import android.provider.Settings
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.SnapshotEntry

/** Screen-off timeout via `Settings.System.SCREEN_OFF_TIMEOUT` (WRITE_SETTINGS). */
class ScreenTimeoutExecutor : AutomationExecutor {

    override val capability = AutomationCapability.SCREEN_TIMEOUT

    override fun support(context: Context): Pair<ActionSupport, String> =
        if (Settings.System.canWrite(context)) {
            ActionSupport.SUPPORTED to "Modify-system-settings access granted"
        } else {
            ActionSupport.USER_ACTION_REQUIRED to
                "Allow Thraksha to modify system settings (one-time, revocable in the " +
                "same screen)"
        }

    override fun snapshot(context: Context): SnapshotEntry {
        val timeout = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrNull()
        return SnapshotEntry(
            capability = capability,
            previousValue = timeout,
            readable = timeout != null,
            detail = timeout?.let { "previous screen timeout: ${it / 1000}s" }
                ?: "screen timeout unreadable",
        )
    }

    override fun apply(context: Context, target: ActionTarget): Boolean {
        val timeout = target.intValue ?: return false
        return Settings.System.putInt(
            context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeout,
        )
    }

    override fun verify(context: Context, target: ActionTarget): Boolean =
        runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrNull() == target.intValue

    override fun restore(context: Context, entry: SnapshotEntry): Boolean {
        val previous = entry.previousValue ?: return false
        return Settings.System.putInt(
            context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, previous,
        )
    }

    override fun verifyRestored(context: Context, entry: SnapshotEntry): Boolean =
        runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrNull() == entry.previousValue
}
