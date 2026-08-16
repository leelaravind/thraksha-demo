package com.thraksha.guardian.automation.executors

import android.content.Context
import android.content.Intent
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.SnapshotEntry

/**
 * Launches an installed application via its launcher intent.
 *
 * Honesty limits (capability matrix §1): resolution + a successful `startActivity`
 * yield **OPENED**, deliberately never ACTED — foreground arrival is not verifiable at
 * Thraksha's privilege and is not claimed. Launching is not a reversible mutation, so
 * there is no snapshot and no restore obligation.
 */
class AppLaunchExecutor : AutomationExecutor {

    override val capability = AutomationCapability.LAUNCH_APP

    /** Support is target-specific; [support] alone reports the general capability. */
    override fun support(context: Context): Pair<ActionSupport, String> =
        ActionSupport.SUPPORTED to "app launching available"

    fun supportFor(context: Context, packageName: String?): Pair<ActionSupport, String> {
        if (packageName == null) {
            return ActionSupport.UNSUPPORTED to "no launch target configured"
        }
        val launchable = runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
        return if (launchable) {
            ActionSupport.SUPPORTED to "launch target installed and launchable"
        } else {
            ActionSupport.UNSUPPORTED to
                "'$packageName' is not installed or has no launcher activity"
        }
    }

    override fun snapshot(context: Context): SnapshotEntry = SnapshotEntry(
        capability = capability,
        readable = false,
        detail = "launching an app is not a reversible mutation; nothing to snapshot",
    )

    override fun apply(context: Context, target: ActionTarget): Boolean {
        val pkg = target.stringValue ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Only "the launch fired without refusal" is verifiable — the engine maps this to OPENED. */
    override fun verify(context: Context, target: ActionTarget): Boolean = true

    override fun restore(context: Context, entry: SnapshotEntry): Boolean = true

    override fun verifyRestored(context: Context, entry: SnapshotEntry): Boolean = true
}
