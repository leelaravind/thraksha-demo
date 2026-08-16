package com.thraksha.guardian.automation

import android.content.Context
import com.thraksha.guardian.automation.executors.AppLaunchExecutor
import com.thraksha.guardian.automation.executors.AutomationExecutor

/** The live-device [CapabilityProbe]: answers straight from the executors. */
class AndroidCapabilityProbe(
    context: Context,
    private val executors: Map<AutomationCapability, AutomationExecutor>,
) : CapabilityProbe {

    private val appContext = context.applicationContext

    override fun support(capability: AutomationCapability): Pair<ActionSupport, String> =
        executors[capability]?.support(appContext)
            ?: (ActionSupport.UNSUPPORTED to "no executor for $capability in this build")

    override fun launchSupport(packageName: String?): Pair<ActionSupport, String> =
        (executors[AutomationCapability.LAUNCH_APP] as? AppLaunchExecutor)
            ?.supportFor(appContext, packageName)
            ?: (ActionSupport.UNSUPPORTED to "no launch executor in this build")

    override fun firstLaunchable(candidates: List<String>): String? =
        candidates.firstOrNull { pkg ->
            runCatching {
                appContext.packageManager.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(false)
        }
}
