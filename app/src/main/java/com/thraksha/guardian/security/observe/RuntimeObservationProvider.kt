package com.thraksha.guardian.security.observe

import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.thraksha.guardian.security.ThrakshaDeviceAdminReceiver
import com.thraksha.guardian.security.evidence.CapabilityObservation
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.inventory.SpecialAccessSnapshot

/**
 * The single Android-facing component for Phase 8.1 runtime observation state
 * (guide §7): live special-access enablement queries plus access to the genuine
 * observation store. No observation logic lives in Compose or the rule engine.
 *
 * Every query is a supported public API (research doc §1):
 *
 *  * overlay — `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)`,
 *    MODE_DEFAULT resolved through the permission grant flag when the caller supplies it;
 *  * notification listener — `NotificationManagerCompat.getEnabledListenerPackages`;
 *  * accessibility — `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`;
 *  * device admin — `DevicePolicyManager.getActiveAdmins`.
 *
 * A query that cannot be answered returns [GrantState.UNKNOWN] — never DENIED/DISABLED
 * (guide §5). Nothing here fabricates Stage 3: usage observation comes exclusively from
 * [RuntimeObservationStore].
 */
class RuntimeObservationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    /** Cached per-provider state: one settings/DPM read serves a whole 441-app scan. */
    private val enabledListenerPackages: Set<String>? by lazy {
        runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(appContext).toSet()
        }.getOrElse {
            Log.w(TAG, "Could not read enabled notification listeners", it)
            null
        }
    }

    private val enabledAccessibilityPackages: Set<String>? by lazy {
        runCatching {
            val raw = Settings.Secure.getString(
                appContext.contentResolver,
                "enabled_accessibility_services",
            ).orEmpty()
            raw.split(':')
                .mapNotNull { entry ->
                    entry.substringBefore('/').trim().takeIf { it.isNotEmpty() }
                }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "Could not read enabled accessibility services", it)
            null
        }
    }

    private val activeAdminPackages: Set<String>? by lazy {
        runCatching {
            ThrakshaDeviceAdminReceiver.getDevicePolicyManager(appContext)
                .activeAdmins.orEmpty()
                .map { it.packageName }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "Could not read active device admins", it)
            null
        }
    }

    /**
     * Live special-access snapshot for one package.
     *
     * @param overlayPermissionGranted the target's SYSTEM_ALERT_WINDOW grant flag from
     * PackageInfo, used only to resolve MODE_DEFAULT — pass null when unknown.
     */
    fun specialAccess(
        packageName: String,
        uid: Int?,
        overlayPermissionGranted: Boolean?,
    ): SpecialAccessSnapshot = SpecialAccessSnapshot(
        overlay = overlayState(packageName, uid, overlayPermissionGranted),
        notificationListener = setMembershipState(packageName, enabledListenerPackages),
        accessibility = setMembershipState(packageName, enabledAccessibilityPackages),
        deviceAdmin = setMembershipState(packageName, activeAdminPackages),
    )

    private fun overlayState(
        packageName: String,
        uid: Int?,
        overlayPermissionGranted: Boolean?,
    ): GrantState {
        val ops = appOps ?: return GrantState.UNKNOWN
        if (uid == null) return GrantState.UNKNOWN
        return runCatching {
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
            }
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> GrantState.ENABLED
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> GrantState.DISABLED
                AppOpsManager.MODE_DEFAULT -> when (overlayPermissionGranted) {
                    true -> GrantState.ENABLED
                    false -> GrantState.DISABLED
                    null -> GrantState.UNKNOWN
                }
                else -> GrantState.UNKNOWN
            }
        }.getOrElse {
            Log.w(TAG, "Overlay op query failed for $packageName", it)
            GrantState.UNKNOWN
        }
    }

    private fun setMembershipState(packageName: String, enabled: Set<String>?): GrantState =
        when {
            enabled == null -> GrantState.UNKNOWN
            packageName in enabled -> GrantState.ENABLED
            else -> GrantState.DISABLED
        }

    /** Genuine runtime observations for one package (newest first). */
    fun observationsFor(packageName: String): List<CapabilityObservation> =
        RuntimeObservationStore.forPackage(packageName)

    /**
     * Convenience for the scan engine: resolves the uid itself and passes the target's
     * own SYSTEM_ALERT_WINDOW grant flag as the MODE_DEFAULT fallback.
     */
    fun specialAccessFor(
        app: com.thraksha.guardian.security.inventory.ObservedApp,
    ): SpecialAccessSnapshot {
        val uid = runCatching {
            appContext.packageManager.getApplicationInfo(app.packageName, 0).uid
        }.getOrNull()
        val overlayGranted =
            if (app.requests(OVERLAY_PERMISSION)) app.isGranted(OVERLAY_PERMISSION) else null
        return specialAccess(app.packageName, uid, overlayGranted)
    }

    private companion object {
        const val TAG = "RuntimeObservation"
        const val OVERLAY_PERMISSION = "android.permission.SYSTEM_ALERT_WINDOW"
    }
}
