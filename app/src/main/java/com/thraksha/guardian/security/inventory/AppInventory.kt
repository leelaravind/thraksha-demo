package com.thraksha.guardian.security.inventory

import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * The registry view a device enumeration ran under. Recorded on every scan result so a
 * build without broad visibility can never present the automatic-visibility subset as a
 * full device scan.
 */
enum class VisibilityScope {
    /** `QUERY_ALL_PACKAGES` is held — the enumeration is the full registry view. */
    FULL,

    /** Only targeted `<queries>` + automatic visibility — a reduced, partial view. */
    REDUCED,
}

/** The outcome of enumerating every visible installed package. */
data class DeviceInventoryResult(
    val apps: List<ObservedApp>,
    val visibilityScope: VisibilityScope,

    /** Packages that were listed but could not be observed (per-package failure). */
    val failedPackages: List<String>,
) {
    val userApps: List<ObservedApp> get() = apps.filterNot { it.isSystemApp }
    val systemApps: List<ObservedApp> get() = apps.filter { it.isSystemApp }
}

/**
 * Turns the packages Thraksha can see into [ObservedApp] snapshots.
 *
 * This is the only class in the security path that touches `PackageManager`. Everything
 * downstream — baselines, rules, the audit engine — works on plain data, so the detection
 * logic can be tested without a device.
 *
 * Two entry points:
 *
 *  * [inventory] — the Phases 1–7 path: snapshots exactly the registered demo packages.
 *  * [inventoryDevice] — the Phase 8 generalized path: enumerates **every package the OS
 *    makes visible** (the full registry while `QUERY_ALL_PACKAGES` is held, which since
 *    Phase 12.1 is every build type; the reduced automatic set otherwise — and the result
 *    says which). A package that fails to observe is recorded as failed, never allowed to
 *    abort the enumeration.
 */
class AppInventory(private val packageManager: PackageManager) {

    /** Snapshots every registered demo package that is currently installed and visible. */
    fun inventory(packages: List<String> = DemoAppRegistry.demoPackages): List<ObservedApp> =
        packages.mapNotNull { observe(it) }

    /**
     * Enumerates every visible installed package on the device.
     *
     * Enumeration is deliberately two-step — a light `getInstalledPackages(0)` for the
     * name list, then one guarded per-package [observe] — so a single hostile or
     * half-uninstalled package cannot take down the whole scan (it lands in
     * [DeviceInventoryResult.failedPackages]), and so no oversized single binder
     * transaction is needed for component metadata.
     */
    fun inventoryDevice(): DeviceInventoryResult {
        @Suppress("DEPRECATION")
        val installed = packageManager.getInstalledPackages(0)
        val apps = mutableListOf<ObservedApp>()
        val failed = mutableListOf<String>()

        installed.mapNotNull { it.packageName }.sorted().forEach { packageName ->
            val observedApp = runCatching { observe(packageName) }.getOrNull()
            if (observedApp != null) apps += observedApp else failed += packageName
        }

        return DeviceInventoryResult(
            apps = apps,
            visibilityScope = visibilityScope(),
            failedPackages = failed,
        )
    }

    /**
     * Whether this build actually holds broad package visibility. Declared in the main
     * manifest since Phase 12.1; still checked live rather than assumed from build type,
     * so a build that ever drops the permission reports REDUCED honestly instead of
     * presenting a partial view as a full device scan.
     */
    fun visibilityScope(): VisibilityScope = runCatching {
        @Suppress("DEPRECATION")
        val own = packageManager.getPackageInfo(
            THRAKSHA_PACKAGE,
            PackageManager.GET_PERMISSIONS,
        )
        if (own.requestedPermissions.orEmpty().contains(QUERY_ALL_PACKAGES)) {
            VisibilityScope.FULL
        } else {
            VisibilityScope.REDUCED
        }
    }.getOrDefault(VisibilityScope.REDUCED)

    /** Snapshots one package, or null if it is not installed or not visible to Thraksha. */
    fun observe(packageName: String): ObservedApp? {
        val info = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_PROVIDERS,
            )
        }.getOrElse { error ->
            // NameNotFoundException here means "not installed" OR "filtered out by
            // package visibility". Both are non-fatal: the audit skips the package.
            Log.i(TAG, "Package $packageName is not visible to Thraksha: ${error.message}")
            return null
        }

        val requested = info.requestedPermissions?.toSet().orEmpty()
        val granted = grantedPermissions(info)
        val appInfo = info.applicationInfo

        return ObservedApp(
            packageName = packageName,
            displayName = appInfo?.let { packageManager.getApplicationLabel(it).toString() }
                ?: packageName,
            appType = DemoAppRegistry.typeOf(packageName),
            requestedPermissions = requested,
            grantedPermissions = granted,
            capabilities = capabilities(info),
            versionName = info.versionName,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            isDebuggable = appInfo?.let { it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 } == true,
            isSystemApp = appInfo?.let {
                it.flags and (ApplicationInfo.FLAG_SYSTEM or
                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            } == true,
            versionCode = versionCodeOf(info),
            isEnabled = appInfo?.enabled != false,
            installerPackage = installerOf(packageName),
            exportedComponents = exportedComponents(info),
        )
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    /** The installing package, when the OS reveals one. Never fatal. */
    private fun installerOf(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    /** Pairs `requestedPermissions` with `requestedPermissionsFlags` to find the granted subset. */
    private fun grantedPermissions(info: PackageInfo): Set<String> {
        val names = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: return emptySet()
        return names.filterIndexed { index, _ ->
            index < flags.size &&
                (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }.toSet()
    }

    /**
     * Reads non-permission capabilities from declared components. A service is identified
     * by the system permission it is required to bind with, which is how Android itself
     * distinguishes an accessibility service from any other service.
     */
    private fun capabilities(info: PackageInfo): Set<Capability> {
        val capabilities = mutableSetOf<Capability>()

        info.services?.forEach { service ->
            when (service.permission) {
                BIND_ACCESSIBILITY -> capabilities += Capability.ACCESSIBILITY_SERVICE
                BIND_NOTIFICATION_LISTENER -> capabilities += Capability.NOTIFICATION_LISTENER
                BIND_VPN -> capabilities += Capability.VPN_SERVICE
            }
        }
        info.receivers?.forEach { receiver ->
            if (receiver.permission == BIND_DEVICE_ADMIN) capabilities += Capability.DEVICE_ADMIN
        }
        return capabilities
    }

    /** Counts the exported component surface as declared in the target's manifest. */
    private fun exportedComponents(info: PackageInfo): ExportedComponents {
        fun Array<out ComponentInfo>?.exported(): List<ComponentInfo> =
            orEmpty().filter { it.exported }

        val services = info.services.exported()
        val receivers = info.receivers.exported()
        val providers = info.providers.exported()

        fun List<ComponentInfo>.unprotected(): Int = count { componentPermission(it) == null }

        return ExportedComponents(
            exportedActivities = info.activities.exported().size,
            exportedServices = services.size,
            exportedReceivers = receivers.size,
            exportedProviders = providers.size,
            unprotectedServices = services.unprotected(),
            unprotectedReceivers = receivers.unprotected(),
            unprotectedProviders = providers.unprotected(),
        )
    }

    /** The permission guarding a component, or null when it is unguarded. */
    private fun componentPermission(component: ComponentInfo): String? = when (component) {
        is android.content.pm.ServiceInfo -> component.permission
        is android.content.pm.ActivityInfo -> component.permission
        is android.content.pm.ProviderInfo ->
            component.readPermission ?: component.writePermission
        else -> null
    }

    private companion object {
        const val TAG = "AppInventory"
        const val THRAKSHA_PACKAGE = "com.thraksha.guardian"
        const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        const val BIND_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"
        const val BIND_NOTIFICATION_LISTENER =
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        const val BIND_VPN = "android.permission.BIND_VPN_SERVICE"
        const val BIND_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN"
    }
}
