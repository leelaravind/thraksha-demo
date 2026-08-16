package com.thraksha.guardian.ui.screens.audit

import com.thraksha.guardian.data.db.AuditEntity
import com.thraksha.guardian.ui.design.StatusTone

/**
 * Turns hash-chained audit rows into a timeline a non-technical person can read.
 *
 * The stored `details` string is engine-authored and technical; it is preserved verbatim
 * for the detail view's technical section. What the timeline shows is a title derived from
 * the entry type (and, for automation rows, the stage prefix the emitter wrote), plus the
 * detail with that prefix stripped. Nothing is invented and nothing is dropped — the raw
 * row is always one tap away.
 */

/** The four consumer categories the guide asks the timeline to use. */
enum class AuditCategory(val label: String) {
    SECURITY("Security"),
    AUTOMATION("Automation"),
    AI("AI interpretation"),
    USER("Your actions"),
}

data class AuditPresentation(
    val category: AuditCategory,
    val title: String,
    val detail: String,
    val tone: StatusTone,
) {
    companion object {

        fun of(entry: AuditEntity): AuditPresentation {
            val stage = entry.details.substringAfter('[', "").substringBefore(']', "")
                .takeIf { entry.details.startsWith("[") && it.isNotEmpty() }
            val body = humanise(
                if (stage != null) entry.details.substringAfter("] ").trim() else entry.details,
            )

            return when (entry.type) {
                "SCAN" -> AuditPresentation(
                    AuditCategory.SECURITY,
                    "Device scan",
                    body,
                    StatusTone.NEUTRAL,
                )

                "THREAT" -> AuditPresentation(
                    AuditCategory.SECURITY,
                    "Security finding recorded",
                    body,
                    StatusTone.DANGER,
                )

                "DECISION" -> AuditPresentation(
                    AuditCategory.SECURITY,
                    "Policy decision",
                    body,
                    StatusTone.WARN,
                )

                "ADVISED" -> AuditPresentation(
                    AuditCategory.SECURITY,
                    "Advice issued",
                    body,
                    StatusTone.WARN,
                )

                "NETWORK" -> AuditPresentation(
                    AuditCategory.SECURITY,
                    "Outbound connection seen",
                    body,
                    StatusTone.WARN,
                )

                "ACTION" -> AuditPresentation(
                    AuditCategory.SECURITY,
                    "Protective action",
                    body,
                    StatusTone.OK,
                )

                "MODE" -> AuditPresentation(
                    AuditCategory.USER,
                    "Execution mode changed",
                    body,
                    StatusTone.NEUTRAL,
                )

                "USER" -> AuditPresentation(
                    AuditCategory.USER,
                    "You responded to an app",
                    body,
                    StatusTone.OK,
                )

                "AUTOMATION" -> automation(stage, body)

                else -> AuditPresentation(
                    AuditCategory.SECURITY,
                    entry.type.lowercase().replaceFirstChar { it.uppercase() },
                    body,
                    StatusTone.NEUTRAL,
                )
            }
        }

        /**
         * Engine tokens → everyday words, for the timeline line only.
         *
         * This is presentation, not redaction: the stored `details` string is rendered
         * verbatim in the event detail view's technical section, so nothing is lost. The
         * substitutions are an explicit table rather than a general regex so a package
         * name or a hash can never be accidentally rewritten.
         */
        internal fun humanise(text: String): String {
            var result = text
            TOKENS.forEach { (token, replacement) -> result = result.replace(token, replacement) }
            return result
        }

        private val TOKENS = listOf(
            "DO_NOT_DISTURB" to "Do Not Disturb",
            "SCREEN_BRIGHTNESS" to "screen brightness",
            "SCREEN_TIMEOUT" to "screen timeout",
            "RINGER_MODE" to "ringer",
            "APP_LAUNCH" to "app launch",
            "MEDIA_VOLUME" to "media volume",
            ": RESTORED" to ": restored",
            ": ROLLED_BACK" to ": rolled back",
            ": RESTORE_FAILED" to ": could not be restored",
            ": NOT_RESTORED" to ": not restored",
        )

        /**
         * Automation rows carry the emitter's stage as a `[STAGE]` prefix. The Ask
         * Thraksha path uses `AI_INTENT_*` stages, which is what separates the AI
         * interpretation category from routine execution.
         */
        private fun automation(stage: String?, body: String): AuditPresentation = when (stage) {
            "AI_INTENT_REQUESTED" -> AuditPresentation(
                AuditCategory.AI,
                "You asked Thraksha",
                body,
                StatusTone.NEUTRAL,
            )

            "AI_INTENT_PARSED" -> AuditPresentation(
                AuditCategory.AI,
                "Thraksha understood the request",
                body,
                StatusTone.OK,
            )

            "AI_INTENT_VALIDATED" -> AuditPresentation(
                AuditCategory.AI,
                "Request passed the safety checks",
                body,
                StatusTone.OK,
            )

            "AI_INTENT_REJECTED" -> AuditPresentation(
                AuditCategory.AI,
                "Request refused",
                body,
                StatusTone.DANGER,
            )

            null -> AuditPresentation(
                AuditCategory.AUTOMATION,
                "Automation",
                body,
                StatusTone.NEUTRAL,
            )

            else -> AuditPresentation(
                AuditCategory.AUTOMATION,
                automationTitle(stage),
                body,
                automationTone(stage),
            )
        }

        /** Stage names as emitted by `AutomationEngine.audit(...)`. */
        private fun automationTitle(stage: String): String = when (stage) {
            "REQUESTED" -> "Routine requested"
            "PLANNED" -> "Routine planned"
            "REFUSED" -> "Routine refused"
            "STARTED" -> "Routine started"
            "ACTION_VERIFIED" -> "Setting changed and verified"
            "ACTION_OPENED" -> "App opened"
            "ACTION_FAILED" -> "A change did not work"
            "ACTIVE" -> "Routine active"
            "ROLLED_BACK" -> "Changes rolled back"
            "RESTORE_REQUESTED" -> "Stopping and restoring"
            "RESTORED" -> "Previous settings restored"
            "RESTORE_FAILED" -> "Settings could not all be restored"
            "STATE_RESTORED" -> "Setting restored"
            "STATE_RESTORE_FAILED" -> "Setting could not be restored"
            "RECOVERED" -> "Routine recovered after restart"
            else -> "Automation · " + stage.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }
        }

        private fun automationTone(stage: String): StatusTone = when (stage) {
            "STARTED", "ACTIVE", "ACTION_VERIFIED", "ACTION_OPENED", "ROLLED_BACK",
            "RESTORED", "STATE_RESTORED", "RECOVERED",
            -> StatusTone.OK

            "REFUSED", "ACTION_FAILED", "RESTORE_FAILED", "STATE_RESTORE_FAILED" ->
                StatusTone.DANGER

            else -> StatusTone.NEUTRAL
        }
    }
}
