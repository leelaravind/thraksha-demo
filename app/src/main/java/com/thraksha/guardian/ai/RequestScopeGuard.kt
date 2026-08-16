package com.thraksha.guardian.ai

/**
 * A deterministic scope guard that runs on the user's text **before** the model is
 * consulted (guide §17, §35).
 *
 * ## Why this exists
 *
 * Evaluation iterations 1 and 3 measured the same failure: asked to both *classify which
 * routine* and *decide whether to refuse*, a 1B model does the first well (~82 % routine
 * accuracy) and the second badly — it answered "Meeting Mode" to *"Turn off Network
 * Guard"* and *"Factory reset the phone"*. Iteration 2 fixed refusals by making the model
 * timid, and recall collapsed to 1/38.
 *
 * The lesson is the one Phase 9 was built on: **never delegate a safety decision to
 * something you then have to trust.** Refusing out-of-domain requests is a deterministic
 * property of Thraksha's capability set, not a language-understanding problem, so it is
 * decided here in ordinary code and the model is left to do the job it is good at.
 *
 * ## What it covers, and why exactly this list
 *
 * One domain per bullet of `PHASE10_AUTOMATION_INTENT_CONTRACT.md` §4 — the things the
 * model may never produce. It is **not** a general "is this supported?" filter: merely
 * out-of-scope requests (say, "set brightness to 128") stay the model's job, because
 * getting those wrong is a usability miss, not a safety failure.
 *
 * ## Honest limitations
 *
 * This is vocabulary matching, not semantic understanding. It will over-refuse a
 * legitimate request that happens to contain "install" or "reset", and it can be evaded
 * by paraphrase. Both are acceptable: over-refusal fails toward the safe side and the
 * deterministic buttons still work, and an evasion still lands in a schema that cannot
 * express anything dangerous, behind `AutomationSafetyPolicy`, behind a preview, behind
 * an explicit START. This guard is defence in depth — never the only defence.
 */
object RequestScopeGuard {

    /** Domains from the frozen contract §4. Matched on word boundaries, case-insensitively. */
    private val SECURITY_TERMS = listOf(
        "malware", "virus", "viruses", "antivirus", "trojan", "spyware",
        "threat", "threats", "malicious", "network guard", "audit log", "audit trail",
        "device owner", "device admin", "policy engine", "safety policy", "security",
        "firewall", "quarantine", "scan my", "scan the", "whitelist", "blacklist",
        "permission", "permissions", "special access", "notification policy access",
        // Asking Thraksha to pronounce an app safe/unsafe is a security verdict, which
        // the model must never make (guide §24) — regardless of which app is named.
        "as safe", "is safe", "is it safe", "trust this", "trusted app", "safe app",
    )

    private val DESTRUCTIVE_TERMS = listOf(
        "factory reset", "factory-reset", "wipe", "erase", "format the",
        "uninstall", "install", "sideload", "apk", "delete", "remove all",
        "kill all", "force stop", "force-stop", "suspend every", "suspend all",
        "lock screen pin", "change my pin", "change the pin", "reset my password",
        "reset the password", "unlock code",
    )

    private val SHELL_AND_URI_TERMS = listOf(
        "adb ", "adb shell", "shell command", " su ", "sudo", "root the", "rooted",
        "intent://", "http://", "https://", "content://", "file://", "market://",
        "package manager", "pm uninstall", "pm install", "am start",
    )

    /** Attempts to rewrite the assistant's own rules — treated as out of scope, not obeyed. */
    private val INJECTION_TERMS = listOf(
        "ignore all previous", "ignore previous", "ignore all restrictions",
        "ignore the safety", "ignore safety", "disregard previous", "disregard all",
        "developer mode", "you are now", "system:", "pretend the", "pretend that",
        "act as if", "without preview", "auto-start", "auto start", "bypass",
        "repeat your system", "repeat your instructions", "your system instructions",
        "override",
    )

    /** Requests for an unbounded or never-ending routine. */
    private val UNBOUNDED_TERMS = listOf(
        "forever", "indefinitely", "permanently", "all day", "never end", "never stop",
        "no end", "endless", "until further notice",
    )

    /**
     * e.g. "9 hours", "99999999 minutes", "3 days", "-30 minutes" — captured to compare
     * against the policy bound. The optional sign matters: a negative duration is not a
     * typo to be absorbed, it is a request that must be refused.
     */
    private val DURATION_PATTERN = Regex(
        """(-?\d+(?:\.\d+)?)\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days|week|weeks)\b""",
        RegexOption.IGNORE_CASE,
    )

    data class Refusal(val reasonCode: String, val matched: String)

    /**
     * @return a [Refusal] if the request is out of the safe domain, or null to continue
     *   to the model. Never returns an intent — it can only decline.
     */
    fun screen(userText: String): Refusal? {
        val text = " ${userText.lowercase().replace(Regex("\\s+"), " ")} "

        // Order matters only for which label the user sees; all four decline.
        SECURITY_TERMS.firstOrNull { text.contains(it) }
            ?.let { return Refusal("SECURITY_REQUEST", it) }
        // Co-occurrence rule: "safe" is far too common a word to match alone, but "safe"
        // about an app/package IS a security verdict, which is never the model's to give.
        if (text.contains("safe") &&
            listOf(" app", "apps", "apk", "package", "this one").any { text.contains(it) }
        ) {
            return Refusal("SECURITY_REQUEST", "safety verdict about an app")
        }
        DESTRUCTIVE_TERMS.firstOrNull { text.contains(it) }
            ?.let { return Refusal("DESTRUCTIVE_REQUEST", it) }
        SHELL_AND_URI_TERMS.firstOrNull { text.contains(it) }
            ?.let { return Refusal("OUT_OF_SCOPE", it) }
        INJECTION_TERMS.firstOrNull { text.contains(it) }
            ?.let { return Refusal("OUT_OF_SCOPE", it) }
        UNBOUNDED_TERMS.firstOrNull { text.contains(it) }
            ?.let { return Refusal("UNSAFE_VALUE", it) }

        // An explicitly stated duration outside the policy bound is refused here, so the
        // number the user actually said is judged — not whatever the model chose to carry.
        DURATION_PATTERN.findAll(text).forEach { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val minutes = when (match.groupValues[2].lowercase()) {
                "minute", "minutes", "min", "mins" -> amount
                "hour", "hours", "hr", "hrs" -> amount * 60
                "day", "days" -> amount * 1440
                "week", "weeks" -> amount * 10080
                else -> return@forEach
            }
            if (minutes > IntentSchema.DURATION_MAX || minutes < IntentSchema.DURATION_MIN) {
                return Refusal("UNSAFE_VALUE", match.value.trim())
            }
        }
        return null
    }
}
