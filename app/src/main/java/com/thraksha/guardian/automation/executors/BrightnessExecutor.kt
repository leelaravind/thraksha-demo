package com.thraksha.guardian.automation.executors

import android.content.Context
import android.provider.Settings
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.SnapshotEntry

/**
 * Screen brightness via `Settings.System` (WRITE_SETTINGS special access).
 *
 * The snapshot captures BOTH the brightness value AND the auto/manual mode (guide §9):
 * this phone runs adaptive brightness, and restoring only the value while leaving the
 * mode forced to manual would corrupt the user's real previous state. Applying a target
 * sets manual mode + value; restoring re-applies the exact previous pair.
 */
class BrightnessExecutor : AutomationExecutor {

    override val capability = AutomationCapability.SCREEN_BRIGHTNESS

    override fun support(context: Context): Pair<ActionSupport, String> =
        if (Settings.System.canWrite(context)) {
            ActionSupport.SUPPORTED to "Modify-system-settings access granted"
        } else {
            ActionSupport.USER_ACTION_REQUIRED to
                "Allow Thraksha to modify system settings (one-time, revocable in the " +
                "same screen)"
        }

    override fun snapshot(context: Context): SnapshotEntry {
        val resolver = context.contentResolver
        val value = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()
        val mode = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull()
        val readable = value != null && mode != null
        return SnapshotEntry(
            capability = capability,
            previousValue = value,
            previousValue2 = mode,
            readable = readable,
            detail = if (readable) {
                "previous brightness: $value/255, mode: ${modeName(mode!!)}"
            } else {
                "brightness state unreadable"
            },
        )
    }

    override fun apply(context: Context, target: ActionTarget): Boolean {
        val value = target.intValue ?: return false
        val resolver = context.contentResolver
        val modeOk = Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val valueOk = Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS, value,
        )
        return modeOk && valueOk
    }

    override fun verify(context: Context, target: ActionTarget): Boolean {
        val resolver = context.contentResolver
        val value = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()
        val mode = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull()
        return value == target.intValue &&
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    }

    override fun restore(context: Context, entry: SnapshotEntry): Boolean {
        val value = entry.previousValue ?: return false
        val mode = entry.previousValue2 ?: return false
        val resolver = context.contentResolver
        val valueOk = Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS, value,
        )
        val modeOk = Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode,
        )
        return valueOk && modeOk
    }

    override fun verifyRestored(context: Context, entry: SnapshotEntry): Boolean {
        val resolver = context.contentResolver
        val value = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()
        val mode = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull()
        return value == entry.previousValue && mode == entry.previousValue2
    }

    private fun modeName(mode: Int): String = when (mode) {
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> "AUTO"
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> "MANUAL"
        else -> "UNKNOWN($mode)"
    }
}
