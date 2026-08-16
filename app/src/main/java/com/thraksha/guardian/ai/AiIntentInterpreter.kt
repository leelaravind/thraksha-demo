package com.thraksha.guardian.ai

import android.content.Context
import com.thraksha.guardian.automation.AutomationEngine
import com.thraksha.guardian.automation.AutomationIntent
import com.thraksha.guardian.automation.AutomationPlan
import com.thraksha.guardian.automation.AutomationPlanner
import com.thraksha.guardian.automation.AutomationSafetyPolicy
import com.thraksha.guardian.automation.RoutineType
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus

/**
 * The bridge from model output to the Phase 9 world (guide §15–§18, §26).
 *
 * It ends at a **plan preview**. It has no path to execution: `AutomationEngine.start` is
 * never called from this file — that call site remains the user's START button, exactly
 * where Phase 9 put it. Inference completing is not authorisation (guide §26).
 *
 * Gate order, none skippable (guide §17):
 *
 *   1. constrained decoding      — `OnDeviceIntentModel` / `ResponseFormat.json`
 *   2. strict parse + contract   — `IntentJsonValidator`
 *   3. `AutomationSafetyPolicy`  — **unmodified Phase 9 code**
 *   4. `AutomationPlanner`       — **unmodified Phase 9 code**, owns what Android will do
 *
 * The model never describes planned Android actions; the deterministic planner does
 * (guide §18).
 */
object AiIntentInterpreter {

    /** Phase 9 UI defaults, applied by the APP when the user gave no duration. */
    private const val DEFAULT_MEETING_MINUTES = 30
    private const val DEFAULT_FOCUS_MINUTES = 60

    /**
     * Curated launch candidates offered to the model. Deliberately NOT the whole device
     * inventory: it protects the token budget and avoids turning the prompt into a device
     * fingerprint. Guardian itself is never offered (Phase 9 FORBIDDEN_LAUNCH_TARGETS).
     */
    private val LAUNCH_CANDIDATES = listOf(
        "com.samsung.android.calendar",
        "com.google.android.calendar",
        "com.google.android.apps.maps",
        "com.waze",
        "com.google.android.keep",
        "com.google.android.apps.docs",
    )

    sealed interface Result {
        /** Ready to show the user. Still requires an explicit START to do anything. */
        data class PlanReady(
            val intent: AutomationIntent,
            val plan: AutomationPlan,
            val understood: String,
        ) : Result

        data class Clarify(val reasonCode: String, val question: String) : Result
        data class Unsupported(val reasonCode: String, val message: String) : Result
        data class Rejected(val stage: String, val message: String) : Result
        data class ModelUnavailable(val message: String) : Result
    }

    fun allowedApps(context: Context): List<String> {
        val packageManager = context.applicationContext.packageManager
        return LAUNCH_CANDIDATES.filter { pkg ->
            runCatching { packageManager.getLaunchIntentForPackage(pkg) != null }
                .getOrDefault(false)
        }
    }

    /**
     * user text → … → plan preview. Never executes.
     *
     * Privacy (guide §39): the raw prompt is never placed on the event bus or in the
     * audit chain — only its length. Audit answers "an AI request happened and what
     * Thraksha concluded", not "what the user typed".
     */
    suspend fun interpret(context: Context, userText: String): Result {
        val appContext = context.applicationContext
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) {
            return Result.Clarify("AMBIGUOUS_ROUTINE", questionFor("AMBIGUOUS_ROUTINE"))
        }
        if (!OnDeviceIntentModel.isLoaded) {
            return Result.ModelUnavailable(LocalModelRepository.current().label)
        }

        audit("AI_INTENT_REQUESTED", "request received (${trimmed.length} chars)")

        // Gate 0 — deterministic scope guard. Runs BEFORE the model, so a request in a
        // domain Thraksha must never touch is refused by ordinary code rather than by a
        // 1B model's judgement (see RequestScopeGuard for the measurements behind this).
        RequestScopeGuard.screen(trimmed)?.let { refusal ->
            audit("AI_INTENT_REJECTED", "out of scope (${refusal.reasonCode})")
            return Result.Unsupported(refusal.reasonCode, messageFor(refusal.reasonCode))
        }

        val apps = allowedApps(appContext)
        val inference = OnDeviceIntentModel.generateIntentJson(trimmed, apps)
        if (!inference.succeeded) {
            audit("AI_INTENT_REJECTED", "inference failed: ${inference.error}")
            return Result.Rejected("INFERENCE", "COULD NOT INTERPRET REQUEST")
        }

        val outcome = IntentJsonValidator.validate(
            inference.rawJson, apps, AppRequestDetector.requestsAnApp(trimmed),
        )
        return when (outcome) {
            is IntentJsonValidator.Outcome.Rejected -> {
                audit("AI_INTENT_REJECTED", "${outcome.stage}: ${outcome.reason}")
                Result.Rejected(outcome.stage, "COULD NOT INTERPRET REQUEST")
            }

            is IntentJsonValidator.Outcome.Clarify -> {
                audit("AI_INTENT_PARSED", "clarification needed (${outcome.reasonCode})")
                Result.Clarify(outcome.reasonCode, questionFor(outcome.reasonCode))
            }

            is IntentJsonValidator.Outcome.Unsupported -> {
                audit("AI_INTENT_PARSED", "unsupported request (${outcome.reasonCode})")
                Result.Unsupported(outcome.reasonCode, messageFor(outcome.reasonCode))
            }

            is IntentJsonValidator.Outcome.Intent -> buildPlan(appContext, outcome)
        }
    }

    private suspend fun buildPlan(
        context: Context,
        outcome: IntentJsonValidator.Outcome.Intent,
    ): Result {
        val routineType = runCatching { RoutineType.valueOf(outcome.routine) }.getOrNull()
            ?: return Result.Rejected("CONTRACT", "COULD NOT INTERPRET REQUEST")

        val intent = AutomationIntent(
            routineType = routineType,
            durationMinutes = outcome.durationMinutes ?: defaultDuration(routineType),
            targetApp = outcome.targetApp,
            // Frozen: model-originated intents never carry custom actions (contract §3).
            customActions = emptyList(),
        )

        // Gate 3 — the UNMODIFIED Phase 9 policy. Model-originated intents traverse the
        // identical object as button-originated ones. There is no "the model was
        // confident" path around it.
        val validation = AutomationSafetyPolicy.validateIntent(intent)
        if (validation is AutomationSafetyPolicy.Validation.Rejected) {
            audit("AI_INTENT_REJECTED", "safety policy: ${validation.reason}")
            return Result.Rejected("SAFETY_POLICY", validation.reason)
        }

        // Gate 4 — the deterministic planner owns what Android will actually do.
        val plan = AutomationPlanner.plan(intent, AutomationEngine.probe(context))
        audit(
            "AI_PLAN_PREVIEWED",
            "${plan.routineLabel}: ${plan.actions.size} action(s), executable=${plan.executable}",
        )
        return Result.PlanReady(intent, plan, understoodLine(intent, plan))
    }

    private fun defaultDuration(type: RoutineType): Int? = when (type) {
        RoutineType.MEETING -> DEFAULT_MEETING_MINUTES
        RoutineType.FOCUS -> DEFAULT_FOCUS_MINUTES
        // Phase 9's Driving default is "until stopped".
        RoutineType.DRIVING -> null
        RoutineType.CUSTOM -> null
    }

    /** App-authored, from the VALIDATED intent — never model prose (guide §18, §23). */
    private fun understoodLine(intent: AutomationIntent, plan: AutomationPlan): String =
        plan.routineLabel + (
            intent.durationMinutes?.let { " — $it minutes" } ?: " — until you stop it"
            )

    /** Fixed questions, selected by reasonCode. The model never writes user-facing text. */
    private fun questionFor(reasonCode: String): String = when (reasonCode) {
        "MISSING_TARGET_APP" -> "Which app would you like me to open?"
        "AMBIGUOUS_DURATION" -> "How many minutes should this last?"
        "MULTIPLE_ROUTINES_REQUESTED" ->
            "I can run one routine at a time — Meeting, Focus or Driving. Which one?"
        else -> "Which would you like — Meeting, Focus or Driving?"
    }

    private fun messageFor(reasonCode: String): String = when (reasonCode) {
        "SECURITY_REQUEST" ->
            "Security decisions aren't made by the assistant. Thraksha's scanner and " +
                "policy handle those, and they can't be changed by a request."
        "DESTRUCTIVE_REQUEST" ->
            "This request isn't available as an automation. Thraksha never deletes, " +
                "installs, resets or removes anything."
        "UNSAFE_VALUE" ->
            "That value is outside the safe range. Routines can run for 1 to 480 minutes."
        "NOT_AN_AUTOMATION_REQUEST" ->
            "That isn't something Thraksha automates. Try Meeting, Focus or Driving."
        else -> "This request isn't available as an automation."
    }

    private suspend fun audit(stage: String, detail: String) {
        SecurityEventBus.emit(SecurityEvent.AutomationEvent(stage, "Ask Thraksha", detail))
    }
}
