package com.thraksha.guardian.security

/**
 * The demo-visible authority mode, derived purely from Android privilege.
 *
 * This is deliberately NOT `ExecutionMode`. `ExecutionMode` is an operator policy dial
 * (how aggressive the guardian may be); `DemoMode` is what the OS actually permits.
 * Only Device Owner can enforce policy on another app, so only Device Owner is
 * FULL POWER. Everything else can observe and advise, and says so.
 *
 * Phases 1–3 implement no enforcement at all — this type only labels the authority the
 * app would have.
 */
enum class DemoMode(val label: String, val explanation: String) {
    FULL_POWER(
        label = "FULL POWER",
        explanation = "Device Owner — Thraksha would be able to act on findings directly.",
    ),
    ADVICE_MODE(
        label = "ADVICE MODE",
        explanation = "Not Device Owner — Thraksha can detect and advise, but cannot act on other apps.",
    );

    companion object {
        fun from(level: PrivilegeLevel): DemoMode = when (level) {
            PrivilegeLevel.DEVICE_OWNER -> FULL_POWER
            PrivilegeLevel.DEVICE_ADMIN, PrivilegeLevel.NORMAL -> ADVICE_MODE
        }
    }
}
