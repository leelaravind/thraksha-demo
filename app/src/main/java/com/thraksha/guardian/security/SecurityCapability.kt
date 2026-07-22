package com.thraksha.guardian.security

import android.content.Context

/** Runtime privilege level. Enforcement (Phase 4) is gated on DEVICE_OWNER via [SecurityCapability.canEnforce]. */
enum class PrivilegeLevel { DEVICE_OWNER, DEVICE_ADMIN, NORMAL }

/**
 * Single source of truth for the app's current privilege level. Consolidates the ad-hoc
 * isDeviceOwner / isAdminActive checks that were scattered across the UI and the
 * DeviceAdminReceiver. Works in any state — it just reports what the app currently is;
 * the device may not be Device Owner yet.
 */
object SecurityCapability {

    fun currentLevel(context: Context): PrivilegeLevel {
        val dpm = ThrakshaDeviceAdminReceiver.getDevicePolicyManager(context)
        val component = ThrakshaDeviceAdminReceiver.getComponentName(context)
        return when {
            dpm.isDeviceOwnerApp(context.packageName) -> PrivilegeLevel.DEVICE_OWNER
            dpm.isAdminActive(component) -> PrivilegeLevel.DEVICE_ADMIN
            else -> PrivilegeLevel.NORMAL
        }
    }

    /** True only when the app can actually enforce policy (Device Owner). */
    fun canEnforce(context: Context): Boolean =
        currentLevel(context) == PrivilegeLevel.DEVICE_OWNER
}
