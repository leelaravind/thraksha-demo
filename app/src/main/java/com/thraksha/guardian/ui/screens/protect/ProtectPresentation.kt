package com.thraksha.guardian.ui.screens.protect

import com.thraksha.guardian.automation.RunPhase
import com.thraksha.guardian.security.FoundationStatus
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.evidence.ContextAssessment
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.inventory.VisibilityScope
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.security.scan.AppScanRecord
import com.thraksha.guardian.security.scan.DeviceScanResult
import com.thraksha.guardian.security.scan.IntelligenceState
import com.thraksha.guardian.security.scan.ScanOutcome
import com.thraksha.guardian.ui.design.StatusTone
import java.util.concurrent.TimeUnit

/**
 * Pure state → consumer-language mapping for the Protect surface.
 *
 * Kept free of Compose and Android so the honesty rules are plain functions that can be
 * read (and tested) on their own. Two rules are enforced here rather than at each call
 * site:
 *
 *  * **No absolute claims.** There is no branch that produces "safe", "secure" or
 *    "protected"; the best outcome the app can state is "No urgent threats found", which
 *    is what the scanner actually establishes.
 *  * **Missing evidence is never clean.** Absent, partial and error states each have their
 *    own wording and never fall through to the reassuring branch.
 */

/** The Protect hero, fully resolved. */
data class ProtectionSummary(
    /** Mono label inside the ring. */
    val state: String,
    val headline: String,
    val supporting: String,
    val tone: StatusTone,
)

/** One dashboard module tile, fully resolved. */
data class ModuleSummary(
    val status: String,
    val supporting: String,
    val tone: StatusTone,
)

object ProtectPresentation {

    /**
     * Items a human is being asked to look at: known-threat matches, advised apps and
     * review-worthy apps, minus anything dismissed for this session.
     */
    fun attentionItems(
        result: DeviceScanResult?,
        dismissed: Set<String>,
    ): List<AppScanRecord> {
        val apps = result?.apps ?: return emptyList()
        return apps
            .filter { it.packageName !in dismissed }
            .filter {
                when (it.displayStatus) {
                    AppDisplayStatus.KNOWN_THREAT_MATCH,
                    AppDisplayStatus.ADVISED,
                    AppDisplayStatus.REVIEW,
                    -> true

                    else -> false
                }
            }
            .sortedBy { severityOrder(it.displayStatus) }
    }

    private fun severityOrder(status: AppDisplayStatus?): Int = when (status) {
        AppDisplayStatus.KNOWN_THREAT_MATCH -> 0
        AppDisplayStatus.ADVISED -> 1
        AppDisplayStatus.REVIEW -> 2
        AppDisplayStatus.PARTIAL -> 3
        AppDisplayStatus.WATCHING -> 4
        null -> 5
    }

    /** Apps whose evidence could not be completed. Surfaced separately — never as clean. */
    fun incompleteItems(result: DeviceScanResult?): List<AppScanRecord> =
        result?.apps?.filter { it.displayStatus == AppDisplayStatus.PARTIAL }.orEmpty()

    fun summary(
        foundation: FoundationStatus.State,
        result: DeviceScanResult?,
        scanning: Boolean,
        attentionCount: Int,
        threatCount: Int,
        now: Long = System.currentTimeMillis(),
    ): ProtectionSummary {
        if (foundation is FoundationStatus.State.Failed) {
            return ProtectionSummary(
                state = "Unavailable",
                headline = "Protection is not running",
                supporting = foundation.message,
                tone = StatusTone.DANGER,
            )
        }
        if (foundation !is FoundationStatus.State.Ready) {
            return ProtectionSummary(
                state = "Starting",
                headline = "Starting up",
                supporting = "Opening secure storage…",
                tone = StatusTone.NEUTRAL,
            )
        }
        if (scanning) {
            return ProtectionSummary(
                state = "Scanning",
                headline = "Checking this device",
                supporting = "Reading the apps installed here.",
                tone = StatusTone.NEUTRAL,
            )
        }
        if (result == null) {
            return ProtectionSummary(
                state = "Ready",
                headline = "Not scanned yet",
                supporting = "Run a scan to see what is installed on this device.",
                tone = StatusTone.NEUTRAL,
            )
        }

        val last = "Last scan ${relativeTime(result.completedAt, now)}"
        return when {
            result.outcome == ScanOutcome.ERROR -> ProtectionSummary(
                state = "Limited",
                headline = "Scan could not finish",
                supporting = result.outcomeDetail ?: last,
                tone = StatusTone.DANGER,
            )

            threatCount > 0 -> ProtectionSummary(
                state = "Attention",
                headline = plural(threatCount, "known threat match", "known threat matches"),
                supporting = last,
                tone = StatusTone.DANGER,
            )

            attentionCount > 0 -> ProtectionSummary(
                state = "Attention",
                headline = plural(attentionCount, "item needs attention", "items need attention"),
                supporting = last,
                tone = StatusTone.WARN,
            )

            result.outcome == ScanOutcome.PARTIAL -> ProtectionSummary(
                state = "Limited",
                headline = "Scan finished with limits",
                supporting = result.outcomeDetail ?: last,
                tone = StatusTone.WARN,
            )

            else -> ProtectionSummary(
                state = "Monitoring",
                headline = "No urgent threats found",
                supporting = last,
                tone = StatusTone.OK,
            )
        }
    }

    fun threatIntelligence(result: DeviceScanResult?): ModuleSummary {
        val intel = result?.intelligence
            ?: return ModuleSummary(
                status = "Not checked",
                supporting = "Run a scan to verify the threat pack.",
                tone = StatusTone.NEUTRAL,
            )
        return when (intel.status) {
            IntelligenceState.Status.ACTIVE -> ModuleSummary(
                status = "Active",
                supporting = buildString {
                    append("Pack v${intel.packVersion ?: "?"} · ")
                    append("${intel.activeIndicators} indicators")
                    if (intel.expiredIndicators > 0) {
                        append(" · ${intel.expiredIndicators} expired")
                    }
                },
                tone = StatusTone.OK,
            )

            IntelligenceState.Status.STALE -> ModuleSummary(
                status = "Stale",
                supporting = "Matching is paused — the pack is past its expiry.",
                tone = StatusTone.WARN,
            )

            IntelligenceState.Status.UNAVAILABLE -> ModuleSummary(
                status = "Unavailable",
                supporting = intel.detail,
                tone = StatusTone.DANGER,
            )
        }
    }

    fun networkGuard(state: NetworkGuard.State): ModuleSummary = when (state) {
        is NetworkGuard.State.Disabled -> ModuleSummary(
            status = "Off",
            supporting = "Outbound monitoring is not running.",
            tone = StatusTone.NEUTRAL,
        )

        is NetworkGuard.State.ConsentRequired -> ModuleSummary(
            status = "Needs consent",
            supporting = "Android must allow the local connection first.",
            tone = StatusTone.WARN,
        )

        is NetworkGuard.State.Starting -> ModuleSummary(
            status = "Starting",
            supporting = "Bringing up the local monitor…",
            tone = StatusTone.WARN,
        )

        is NetworkGuard.State.Active -> ModuleSummary(
            status = "On",
            supporting = "Watching ${shortPackage(state.scopedPackage)} · " +
                "${state.packetsObserved} seen, ${state.packetsBlocked} blocked",
            tone = StatusTone.OK,
        )

        is NetworkGuard.State.Error -> ModuleSummary(
            status = "Error",
            supporting = state.reason,
            tone = StatusTone.DANGER,
        )
    }

    fun appScanner(result: DeviceScanResult?, attentionCount: Int): ModuleSummary {
        if (result == null) {
            return ModuleSummary(
                status = "Not run",
                supporting = "No scan on this device yet.",
                tone = StatusTone.NEUTRAL,
            )
        }
        val analysed = "${result.analyzedCount} apps analysed"
        return when {
            result.outcome == ScanOutcome.ERROR -> ModuleSummary(
                status = "Error",
                supporting = result.outcomeDetail ?: "The scan did not complete.",
                tone = StatusTone.DANGER,
            )

            attentionCount > 0 -> ModuleSummary(
                status = plural(attentionCount, "to review", "to review"),
                supporting = analysed,
                tone = StatusTone.WARN,
            )

            result.outcome == ScanOutcome.PARTIAL ||
                result.visibilityScope == VisibilityScope.REDUCED -> ModuleSummary(
                status = "Partial",
                supporting = "$analysed · some checks could not complete",
                tone = StatusTone.WARN,
            )

            else -> ModuleSummary(
                status = "No findings",
                supporting = analysed,
                tone = StatusTone.OK,
            )
        }
    }

    fun automation(phase: RunPhase, routineLabel: String?): ModuleSummary = when (phase) {
        RunPhase.IDLE, RunPhase.PREFLIGHT -> ModuleSummary(
            status = "Ready",
            supporting = "No routine is running.",
            tone = StatusTone.NEUTRAL,
        )

        RunPhase.EXECUTING -> ModuleSummary(
            status = "Starting",
            supporting = "Applying ${routineLabel ?: "routine"}…",
            tone = StatusTone.WARN,
        )

        RunPhase.ACTIVE -> ModuleSummary(
            status = "Active",
            supporting = "${routineLabel ?: "Routine"} is running.",
            tone = StatusTone.OK,
        )

        RunPhase.PARTIAL -> ModuleSummary(
            status = "Partly active",
            supporting = "${routineLabel ?: "Routine"} — some changes need you.",
            tone = StatusTone.WARN,
        )

        RunPhase.RESTORING -> ModuleSummary(
            status = "Restoring",
            supporting = "Putting your previous settings back…",
            tone = StatusTone.WARN,
        )

        RunPhase.RESTORED -> ModuleSummary(
            status = "Ready",
            supporting = "Previous settings restored.",
            tone = StatusTone.NEUTRAL,
        )

        RunPhase.RESTORE_FAILED -> ModuleSummary(
            status = "Check",
            supporting = "Some settings could not be restored.",
            tone = StatusTone.DANGER,
        )

        RunPhase.FAILED -> ModuleSummary(
            status = "Failed",
            supporting = "The last routine did not run.",
            tone = StatusTone.DANGER,
        )
    }

    // --- per-app presentation -------------------------------------------------

    fun statusLabel(status: AppDisplayStatus?): String = when (status) {
        AppDisplayStatus.KNOWN_THREAT_MATCH -> "Known threat"
        AppDisplayStatus.ADVISED -> "Advice issued"
        AppDisplayStatus.REVIEW -> "Review"
        AppDisplayStatus.WATCHING -> "Watching"
        AppDisplayStatus.PARTIAL -> "Incomplete"
        null -> "Incomplete"
    }

    fun statusTone(status: AppDisplayStatus?): StatusTone = when (status) {
        AppDisplayStatus.KNOWN_THREAT_MATCH, AppDisplayStatus.ADVISED -> StatusTone.DANGER
        AppDisplayStatus.REVIEW -> StatusTone.WARN
        AppDisplayStatus.WATCHING -> StatusTone.OK
        AppDisplayStatus.PARTIAL, null -> StatusTone.NEUTRAL
    }

    /**
     * The one-line, non-technical reason a card is on screen. Derived from the strongest
     * evidence the record actually holds — never from the app's name or category.
     */
    fun plainSummary(record: AppScanRecord): String {
        val observed = record.evidence.firstOrNull { it.observed }
        if (observed != null) {
            return "Thraksha saw this app use ${observed.capabilityLabel.lowercase()}."
        }
        val granted = record.evidence.filter {
            it.grantState == GrantState.GRANTED || it.grantState == GrantState.ENABLED
        }
        if (granted.isNotEmpty()) {
            val names = granted.take(2).joinToString(" and ") { it.capabilityLabel.lowercase() }
            val extra = if (granted.size > 2) " and ${granted.size - 2} more" else ""
            return "This app is currently allowed to use $names$extra."
        }
        val declared = record.evidence.count { it.declared }
        if (declared > 0) {
            return "This app asks for $declared sensitive " +
                (if (declared == 1) "capability" else "capabilities") +
                ", but Android has not granted them."
        }
        return "No capability evidence was collected for this app."
    }

    fun contextLine(assessment: ContextAssessment): String = when (assessment) {
        ContextAssessment.EXPECTED -> "Normal for this kind of app"
        ContextAssessment.UNUSUAL -> "Unusual for this kind of app"
        ContextAssessment.SUSPICIOUS -> "Does not fit this kind of app"
        ContextAssessment.UNKNOWN -> "Not enough information to judge"
    }

    fun contextTone(assessment: ContextAssessment): StatusTone = when (assessment) {
        ContextAssessment.EXPECTED -> StatusTone.OK
        ContextAssessment.UNUSUAL -> StatusTone.WARN
        ContextAssessment.SUSPICIOUS -> StatusTone.DANGER
        ContextAssessment.UNKNOWN -> StatusTone.NEUTRAL
    }

    // --- helpers --------------------------------------------------------------

    fun plural(count: Int, singular: String, plural: String): String =
        if (count == 1) "1 $singular" else "$count $plural"

    fun shortPackage(packageName: String): String =
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    /** "just now" / "12 minutes ago" / "3 hours ago" / "2 days ago". */
    fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = (now - epochMillis).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> plural(minutes.toInt(), "minute ago", "minutes ago")
            hours < 24 -> plural(hours.toInt(), "hour ago", "hours ago")
            days < 30 -> plural(days.toInt(), "day ago", "days ago")
            else -> "over a month ago"
        }
    }
}
