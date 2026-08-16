package com.thraksha.guardian.security.baseline

import com.thraksha.guardian.security.inventory.AppType

/**
 * What a given kind of app is *expected* to need.
 *
 * This is the "APP TYPE" half of `APP TYPE + CAPABILITY + CONTEXT → VERDICT`. Without it
 * a rule could only say "this app requests READ_MEDIA_IMAGES", which is true of a gallery
 * app and tells you nothing. With it, the same observation becomes "this app requests
 * photo access and its role does not need it".
 *
 * @param expectedPermissions permissions that are normal for the role. A rule will not
 *   flag a permission that appears here, even if the rule otherwise targets it — which is
 *   what makes the same capability acceptable for one type and abnormal for another.
 */
data class AppBaseline(
    val appType: AppType,
    val roleDescription: String,
    val expectedPermissions: Set<String>,
) {
    fun isExpected(permission: String): Boolean = permission in expectedPermissions
}

/**
 * The deterministic baseline table.
 *
 * Hard-coded on purpose (guide §3.2): no learning, no inference, no per-device drift, so a
 * given app produces the same verdict on every run and on every device. Only [AppType.CALLER_ID]
 * is populated — it is the only type this demo needs. Adding MESSAGING or BANKING later is a
 * new entry here plus an `appTypes` value in the signed rulepack; the engine does not change.
 */
object AppBaselines {

    /** The minimum reasonable caller-ID profile: identify a number, match it, notify. */
    private val CALLER_ID = AppBaseline(
        appType = AppType.CALLER_ID,
        roleDescription = "Identifies incoming callers by matching numbers against contacts " +
            "and call history",
        expectedPermissions = setOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.CALL_PHONE",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.VIBRATE",
        ),
    )

    private val baselines: Map<AppType, AppBaseline> = mapOf(
        AppType.CALLER_ID to CALLER_ID,
    )

    /** The baseline for [appType], or null when no baseline is defined for it. */
    fun forType(appType: AppType): AppBaseline? = baselines[appType]

    /** True when a verdict can be reached for this type at all. */
    fun hasBaseline(appType: AppType): Boolean = baselines.containsKey(appType)
}
