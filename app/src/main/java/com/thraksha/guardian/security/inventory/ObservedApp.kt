package com.thraksha.guardian.security.inventory

/**
 * A snapshot of everything Thraksha can genuinely observe about another installed app.
 *
 * This is the boundary between Android and the security engine. `RuleEngine` never sees
 * a `PackageInfo`, a `PackageManager` or a `Context` — it evaluates this immutable value,
 * which is why the engine is unit-testable off-device with no Robolectric.
 *
 * **Every field here comes from a supported, documented API.** There is deliberately no
 * field for "apps this app has read", "files it opened" or "hosts it contacted": Android
 * exposes no way for a normal app to observe another app's media reads, package-list
 * queries or TLS payloads, so no such field is invented.
 *
 * Phase 8 extends the snapshot for generalized (arbitrary-app) scanning. All new fields
 * default to the pre-Phase-8 behaviour so the controlled demo pipeline is unchanged.
 */
data class ObservedApp(
    val packageName: String,
    val displayName: String,
    val appType: AppType,

    /** Permissions declared in the target's manifest (`PackageInfo.requestedPermissions`). */
    val requestedPermissions: Set<String>,

    /**
     * The subset of [requestedPermissions] the OS reports as currently granted
     * (`REQUESTED_PERMISSION_GRANTED`). A dangerous permission that is requested but not
     * yet granted is still a real profile mismatch — the app has declared the intent to
     * hold it — so detection is based on the requested set, and this is evidence.
     */
    val grantedPermissions: Set<String>,

    /** Non-permission capabilities read from package metadata (see [Capability]). */
    val capabilities: Set<Capability>,

    val versionName: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,

    /** True when signed with the platform debug key — demo builds are, so this is context only. */
    val isDebuggable: Boolean,

    /** True for apps in the system image (including updated system apps). */
    val isSystemApp: Boolean,

    // --- Phase 8 additions (defaults keep Phases 1–7 call sites source-compatible) ---

    /** `PackageInfo.longVersionCode` (or legacy `versionCode` below API 28). */
    val versionCode: Long = 0L,

    /** `ApplicationInfo.enabled` — a disabled app is still inventoried, and said to be disabled. */
    val isEnabled: Boolean = true,

    /**
     * The package that installed this app (`InstallSourceInfo.installingPackageName`),
     * when Android exposes it. Null for system-image apps, adb side-loads on some
     * OS versions, and any case the OS declines to reveal.
     */
    val installerPackage: String? = null,

    /** Exported-component attack surface read from the target's manifest via PackageManager. */
    val exportedComponents: ExportedComponents = ExportedComponents.NONE,

    // --- Phase 8.1 addition ---

    /**
     * Live special-access enablement states (overlay / notification listener /
     * accessibility / device admin), queried by `RuntimeObservationProvider` and attached
     * by the scan engine. Defaults to all-UNKNOWN so Phases 1–8 call sites are unchanged
     * and an uncollected snapshot never claims DISABLED.
     */
    val specialAccess: SpecialAccessSnapshot = SpecialAccessSnapshot.UNKNOWN,
) {
    fun requests(permission: String): Boolean = permission in requestedPermissions
    fun isGranted(permission: String): Boolean = permission in grantedPermissions
}

/**
 * Structured summary of a package's exported components — the surface other apps can
 * invoke. **A count here is evidence for review, never malware proof**: every launchable
 * app exports at least its launcher activity.
 *
 * "Unprotected" means `exported="true"` with no `android:permission` guard, as read from
 * `PackageInfo`. Intent-filter details are not inspected; this is a breadth signal only.
 */
data class ExportedComponents(
    val exportedActivities: Int,
    val exportedServices: Int,
    val exportedReceivers: Int,
    val exportedProviders: Int,
    val unprotectedServices: Int,
    val unprotectedReceivers: Int,
    val unprotectedProviders: Int,
) {
    val totalExported: Int
        get() = exportedActivities + exportedServices + exportedReceivers + exportedProviders

    /** Non-activity components that are exported with no permission protection. */
    val unprotectedNonActivity: Int
        get() = unprotectedServices + unprotectedReceivers + unprotectedProviders

    companion object {
        val NONE = ExportedComponents(0, 0, 0, 0, 0, 0, 0)
    }
}

/**
 * Observable, non-permission app capabilities.
 *
 * Kept separate from raw permission strings so a rule can talk about *what an app can do*
 * rather than which constant happens to encode it on a given API level. Component-derived
 * capabilities come from the system `BIND_*` permission the component requires; the OS
 * itself uses the same convention to identify these service types.
 */
enum class Capability {
    /** Declares an accessibility service. */
    ACCESSIBILITY_SERVICE,

    /** Declares a notification listener service. */
    NOTIFICATION_LISTENER,

    /** Declares a VpnService. */
    VPN_SERVICE,

    /** Declares a device-admin receiver. */
    DEVICE_ADMIN,
}
