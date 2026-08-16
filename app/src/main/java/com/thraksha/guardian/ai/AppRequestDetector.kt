package com.thraksha.guardian.ai

/**
 * Answers one narrow question about the user's text: **did they ask for an app to be
 * opened?**
 *
 * This exists to implement a rule the frozen contract already states
 * (`PHASE10_AUTOMATION_INTENT_CONTRACT.md` §6): *"target app named but ambiguous / not
 * installed → CLARIFY / MISSING_TARGET_APP — the app asks; it does not pick one."*
 *
 * It is a **resolution** question, not a language-understanding one: Thraksha knows which
 * apps it offered, so it knows when it failed to resolve one. Pairing that with "the user
 * used an opening verb" is enough to ask instead of quietly opening nothing — or worse,
 * opening whatever the planner's default candidate happens to be.
 *
 * It can only ever cause Thraksha to **ask a question**. It cannot create an intent,
 * choose an app, or change any parameter.
 */
object AppRequestDetector {

    private val OPEN_VERBS = listOf(
        "open ", "launch ", "start up ", "bring up ", "pull up ", "fire up ", "run ",
        "show me ", "switch to ",
    )

    /**
     * True when the text asks for something to be opened.
     *
     * Deliberately just the verb: whether the *object* was resolvable is answered by the
     * model's `targetApp` (constrained to genuinely installed packages), so this only has
     * to spot the request. Conservative by design — a false negative leaves the model's
     * judgement standing, and a false positive costs one extra question.
     */
    fun requestsAnApp(userText: String): Boolean {
        val text = " ${userText.lowercase().replace(Regex("\\s+"), " ")} "
        return OPEN_VERBS.any { text.contains(it) }
    }
}
