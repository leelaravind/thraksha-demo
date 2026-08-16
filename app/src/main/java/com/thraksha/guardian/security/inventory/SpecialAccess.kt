package com.thraksha.guardian.security.inventory

/**
 * Grant/enablement state of one capability for one app (Phase 8.1, guide §5).
 *
 * Runtime permissions use GRANTED/DENIED; user-enabled special access (overlay,
 * notification listener, accessibility, device admin) uses ENABLED/DISABLED — the two
 * vocabularies are deliberately not merged, because Android models them differently and
 * pretending otherwise would misstate what was checked.
 *
 * UNKNOWN means the state could not be determined at Thraksha's privilege. It is never
 * collapsed into DENIED/DISABLED (guide §5: unknown must not masquerade as denied).
 */
enum class GrantState {
    GRANTED,
    DENIED,
    ENABLED,
    DISABLED,
    UNKNOWN,

    /** The stage does not apply (e.g. install-time normal permission with no user gate). */
    NOT_APPLICABLE,
}

/**
 * The live special-access enablement states Android exposes for another package
 * (Phase 8.1 research doc §1–2). Every field is the result of a supported query:
 *
 *  * [overlay] — `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_SYSTEM_ALERT_WINDOW)`, with
 *    MODE_DEFAULT resolved via the permission grant flag, else UNKNOWN;
 *  * [notificationListener] — `NotificationManagerCompat.getEnabledListenerPackages`;
 *  * [accessibility] — `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`;
 *  * [deviceAdmin] — `DevicePolicyManager.getActiveAdmins`.
 *
 * The default [UNKNOWN] snapshot keeps every Phase 1–8 call site source-compatible and
 * is itself honest: a snapshot nobody collected is unknown, not disabled.
 */
data class SpecialAccessSnapshot(
    val overlay: GrantState = GrantState.UNKNOWN,
    val notificationListener: GrantState = GrantState.UNKNOWN,
    val accessibility: GrantState = GrantState.UNKNOWN,
    val deviceAdmin: GrantState = GrantState.UNKNOWN,
) {
    companion object {
        val UNKNOWN = SpecialAccessSnapshot()
    }
}
