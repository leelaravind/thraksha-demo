package com.thraksha.guardian.security.inventory

/**
 * What an app is *for*.
 *
 * The whole detection principle is `APP TYPE + CAPABILITY + CONTEXT → VERDICT`, never
 * `PERMISSION X = MALWARE`. The same capability is legitimate for one type and abnormal
 * for another, so no verdict can be reached without a type.
 *
 * Only [CALLER_ID] has a baseline in this run. The other constants exist because the
 * baseline layer and the rulepack are both keyed by type name, so adding a real
 * MESSAGING or BANKING baseline later is a data change, not an engine change.
 */
enum class AppType {
    CALLER_ID,
    MESSAGING,
    BANKING,
    GAME,

    /** No demo classification. Apps of unknown type are not evaluated. */
    UNKNOWN,
}

/**
 * Deterministic `packageName → AppType` mapping for the controlled demo sandbox.
 *
 * Hard-coded and exhaustive by design (guide §2.4): no inference, no heuristics, no
 * machine learning. A demo whose classification step is itself a guess cannot produce a
 * trustworthy verdict. Anything not listed here is [AppType.UNKNOWN] and is skipped by
 * the audit rather than guessed at.
 */
object DemoAppRegistry {

    const val GOOD_CALLER = "com.thraksha.demo.goodcaller"
    const val VILLAIN_CALLER = "com.thraksha.demo.villaincaller"

    private val typesByPackage: Map<String, AppType> = mapOf(
        GOOD_CALLER to AppType.CALLER_ID,
        VILLAIN_CALLER to AppType.CALLER_ID,
    )

    /** Every package the audit is allowed to look at. Matches the manifest `<queries>`. */
    val demoPackages: List<String> = typesByPackage.keys.toList()

    fun typeOf(packageName: String): AppType = typesByPackage[packageName] ?: AppType.UNKNOWN

    fun isDemoPackage(packageName: String): Boolean = typesByPackage.containsKey(packageName)
}
