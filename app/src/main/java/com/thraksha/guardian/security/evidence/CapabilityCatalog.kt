package com.thraksha.guardian.security.evidence

import com.thraksha.guardian.security.inventory.Capability

/**
 * The fixed set of capabilities the Phase 8.1 evidence progression reports on, with the
 * exact permission strings / component kinds each stage is derived from and the honest
 * Stage 3 limitation for capabilities whose usage Android does not expose
 * (research doc temp/PHASE8_1_RUNTIME_OBSERVABILITY.md §2).
 *
 * Pure data — no Android types — so the assembler and every test can run off-device.
 */
object CapabilityCatalog {

    const val CONTACTS = "contacts"
    const val LOCATION = "location"
    const val CAMERA = "camera"
    const val MICROPHONE = "microphone"
    const val MEDIA = "media"
    const val SMS = "sms"
    const val ACCESSIBILITY = "accessibility"
    const val OVERLAY = "overlay"
    const val NOTIFICATION_LISTENER = "notification-listener"
    const val DEVICE_ADMIN = "device-admin"
    const val PACKAGE_VISIBILITY = "package-visibility"
    const val NETWORK = "network"
    const val NOTIFICATION_POSTING = "notification-posting"

    /** Standard limitation for capabilities with no usage API at Thraksha's privilege. */
    const val NOT_OBSERVABLE =
        "NOT OBSERVABLE — Android exposes per-app usage of this capability only to " +
            "privileged callers (GET_APP_OPS_STATS); no verified observation is possible " +
            "at Thraksha's privilege, and none is invented."

    /** Limitation for capabilities Thraksha has a genuine source for that saw nothing. */
    const val NO_OBSERVATION_YET =
        "No verified observation available — the observation source has not seen this " +
            "app exercise the capability."

    /** Permission-backed capability groups (Stage 1 = requested, Stage 2 = granted). */
    data class PermissionCapability(
        val id: String,
        val label: String,
        val permissions: Set<String>,
        /** True when Thraksha has a genuine Stage 3 source for this capability. */
        val observableByThraksha: Boolean = false,
        val observabilityNote: String = NOT_OBSERVABLE,
    )

    val PERMISSION_CAPABILITIES: List<PermissionCapability> = listOf(
        PermissionCapability(
            CONTACTS, "Contacts",
            setOf(
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
            ),
        ),
        PermissionCapability(
            LOCATION, "Location",
            setOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
            ),
        ),
        PermissionCapability(
            CAMERA, "Camera",
            setOf("android.permission.CAMERA"),
        ),
        PermissionCapability(
            MICROPHONE, "Microphone",
            setOf("android.permission.RECORD_AUDIO"),
        ),
        PermissionCapability(
            MEDIA, "Media / storage",
            setOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
            ),
        ),
        PermissionCapability(
            SMS, "SMS",
            setOf(
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.SEND_SMS",
            ),
        ),
        PermissionCapability(
            NETWORK, "Network",
            setOf("android.permission.INTERNET"),
            observableByThraksha = true,
            observabilityNote = "Observed only for packages scoped into the Network Guard " +
                "tunnel while it is active; other apps' traffic is NOT OBSERVABLE.",
        ),
        PermissionCapability(
            PACKAGE_VISIBILITY, "Package visibility",
            setOf("android.permission.QUERY_ALL_PACKAGES"),
        ),
    )

    /** Special-access capabilities: Stage 1 from components/permissions, Stage 2 from live state. */
    enum class SpecialAccessKind { OVERLAY_ACCESS, NOTIFICATION_LISTENER_ACCESS, ACCESSIBILITY_ACCESS, DEVICE_ADMIN_ACTIVE }

    data class SpecialCapability(
        val id: String,
        val label: String,
        val kind: SpecialAccessKind,
        /** Component-derived declaration, when one exists. */
        val declaredByCapability: Capability?,
        /** Permission-derived declaration, when one exists (overlay). */
        val declaredByPermission: String?,
        val declaredEvidenceText: String,
    )

    val SPECIAL_CAPABILITIES: List<SpecialCapability> = listOf(
        SpecialCapability(
            ACCESSIBILITY, "Accessibility service",
            SpecialAccessKind.ACCESSIBILITY_ACCESS,
            declaredByCapability = Capability.ACCESSIBILITY_SERVICE,
            declaredByPermission = null,
            declaredEvidenceText =
            "declares a service requiring android.permission.BIND_ACCESSIBILITY_SERVICE",
        ),
        SpecialCapability(
            OVERLAY, "Draw over other apps",
            SpecialAccessKind.OVERLAY_ACCESS,
            declaredByCapability = null,
            declaredByPermission = "android.permission.SYSTEM_ALERT_WINDOW",
            declaredEvidenceText = "declares android.permission.SYSTEM_ALERT_WINDOW",
        ),
        SpecialCapability(
            NOTIFICATION_LISTENER, "Notification listener",
            SpecialAccessKind.NOTIFICATION_LISTENER_ACCESS,
            declaredByCapability = Capability.NOTIFICATION_LISTENER,
            declaredByPermission = null,
            declaredEvidenceText =
            "declares a service requiring android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        ),
        SpecialCapability(
            DEVICE_ADMIN, "Device admin",
            SpecialAccessKind.DEVICE_ADMIN_ACTIVE,
            declaredByCapability = Capability.DEVICE_ADMIN,
            declaredByPermission = null,
            declaredEvidenceText =
            "declares a receiver requiring android.permission.BIND_DEVICE_ADMIN",
        ),
    )
}
