package com.thraksha.guardian.security

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Admin Receiver for Thraksha Guardian
 *
 * Enables Device Owner mode via ADB command:
 * adb shell dpm set-device-owner com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver
 *
 * Device Owner capabilities:
 * - System-level lockdown
 * - Network restrictions
 * - App installation controls
 * - Factory reset protection
 *
 * CRITICAL: Can only be set on freshly reset devices (no accounts added)
 */
class ThrakshaDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "ThrakshaDeviceAdmin"

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        fun getDevicePolicyManager(context: Context): DevicePolicyManager {
            return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        }

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ThrakshaDeviceAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Thraksha Device Admin enabled")
        Toast.makeText(
            context,
            "Thraksha Device Admin enabled",
            Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Thraksha Device Admin disabled")
        Toast.makeText(
            context,
            "Thraksha Device Admin disabled - Security features limited",
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i(TAG, "Lock Task Mode entered for: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i(TAG, "Lock Task Mode exited")
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordChanged(context, intent, user)
        Log.d(TAG, "Password changed for user")
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordFailed(context, intent, user)
        Log.w(TAG, "Password failed attempt")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        Log.d(TAG, "Password succeeded")
    }
}
