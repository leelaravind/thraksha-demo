package com.thraksha.guardian.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.thraksha.guardian.ui.design.ThrakshaBottomNavigation
import com.thraksha.guardian.ui.design.ThrakshaDestination
import com.thraksha.guardian.ui.screens.audit.AuditEventDetailScreen
import com.thraksha.guardian.ui.screens.audit.AuditScreen
import com.thraksha.guardian.ui.screens.automate.AutomateScreen
import com.thraksha.guardian.ui.screens.protect.FindingDetailScreen
import com.thraksha.guardian.ui.screens.protect.FindingsScreen
import com.thraksha.guardian.ui.screens.protect.NetworkGuardScreen
import com.thraksha.guardian.ui.screens.protect.ProtectScreen
import com.thraksha.guardian.ui.screens.protect.ThreatIntelligenceScreen
import com.thraksha.guardian.ui.screens.settings.AboutScreen
import com.thraksha.guardian.ui.screens.settings.AppearanceScreen
import com.thraksha.guardian.ui.screens.settings.DiagnosticsScreen
import com.thraksha.guardian.ui.screens.settings.OnDeviceAiScreen
import com.thraksha.guardian.ui.screens.settings.PermissionsScreen
import com.thraksha.guardian.ui.screens.settings.PolicyCheckScreen
import com.thraksha.guardian.ui.screens.settings.PrivacyDataScreen
import com.thraksha.guardian.ui.screens.settings.ProtectionSettingsScreen
import com.thraksha.guardian.ui.screens.settings.SettingsScreen

/**
 * Navigation for the Phase 11B shell.
 *
 * Deliberately hand-rolled rather than pulling in `navigation-compose`: the app has
 * exactly three top-level destinations plus a handful of detail screens, and this keeps
 * the offline build's dependency set unchanged.
 *
 * Each tab owns its own back stack, so switching Protect → Audit → Protect returns to the
 * finding you were reading rather than resetting the tab. System back pops the current
 * tab's stack first and only then leaves the app.
 */
sealed class Route(val key: String) {
    // Top-level
    data object Protect : Route("protect")
    data object Automate : Route("automate")
    data object Audit : Route("audit")

    // Protect stack
    data object Findings : Route("findings")
    data class FindingDetail(val packageName: String) : Route("finding:$packageName")
    data object ThreatIntel : Route("threat-intel")
    data object NetworkGuard : Route("network-guard")

    // Audit stack
    data class AuditDetail(val entryId: Long) : Route("audit-entry:$entryId")

    // Settings (reachable from any tab's app bar)
    data object Settings : Route("settings")
    data object Appearance : Route("settings/appearance")
    data object Protection : Route("settings/protection")
    data object Permissions : Route("settings/permissions")
    data object OnDeviceAi : Route("settings/ai")
    data object PrivacyData : Route("settings/privacy")
    data object About : Route("settings/about")
    data object Diagnostics : Route("settings/diagnostics")
    data object PolicyCheck : Route("settings/policy-check")

    companion object {
        /** Rebuilds a route from its saved key (process-death restore). */
        fun parse(key: String): Route = when {
            key.startsWith("finding:") -> FindingDetail(key.removePrefix("finding:"))
            key.startsWith("audit-entry:") ->
                AuditDetail(key.removePrefix("audit-entry:").toLongOrNull() ?: 0L)
            else -> listOf(
                Protect, Automate, Audit, Findings, ThreatIntel, NetworkGuard,
                Settings, Appearance, Protection, Permissions, OnDeviceAi,
                PrivacyData, About, Diagnostics, PolicyCheck,
            ).firstOrNull { it.key == key } ?: Protect
        }
    }
}

private val stackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

@Composable
fun ThrakshaNavHost() {
    var tab by rememberSaveable { mutableStateOf(ThrakshaDestination.PROTECT.name) }
    val current = ThrakshaDestination.valueOf(tab)

    // One stack per tab; the tab root itself is implicit (empty stack == root).
    val protectStack = rememberSaveable(saver = stackSaver) { mutableStateListOf() }
    val automateStack = rememberSaveable(saver = stackSaver) { mutableStateListOf() }
    val auditStack = rememberSaveable(saver = stackSaver) { mutableStateListOf() }

    val stack = when (current) {
        ThrakshaDestination.PROTECT -> protectStack
        ThrakshaDestination.AUTOMATE -> automateStack
        ThrakshaDestination.AUDIT -> auditStack
    }

    val route: Route = stack.lastOrNull()?.let { Route.parse(it) } ?: when (current) {
        ThrakshaDestination.PROTECT -> Route.Protect
        ThrakshaDestination.AUTOMATE -> Route.Automate
        ThrakshaDestination.AUDIT -> Route.Audit
    }

    fun push(destination: Route) {
        stack.add(destination.key)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    // System back pops within the tab. At a tab root it falls through to the system,
    // which finishes the activity — the expected Android behaviour for a root screen.
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val bottomBar: @Composable () -> Unit = {
        ThrakshaBottomNavigation(
            current = current,
            onSelect = { selected ->
                if (selected == current) {
                    // Re-tapping the active tab returns to its root.
                    stack.clear()
                } else {
                    tab = selected.name
                }
            },
        )
    }

    AnimatedContent(
        targetState = route.key,
        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
        label = "route",
    ) { key ->
        when (val destination = Route.parse(key)) {
            Route.Protect -> ProtectScreen(
                bottomBar = bottomBar,
                onOpenFindings = { push(Route.Findings) },
                onOpenFinding = { pkg -> push(Route.FindingDetail(pkg)) },
                onOpenThreatIntel = { push(Route.ThreatIntel) },
                onOpenNetworkGuard = { push(Route.NetworkGuard) },
                onOpenAutomate = { tab = ThrakshaDestination.AUTOMATE.name },
                onOpenSettings = { push(Route.Settings) },
            )

            Route.Automate -> AutomateScreen(
                bottomBar = bottomBar,
                onOpenSettings = { push(Route.Settings) },
                onOpenAi = { push(Route.OnDeviceAi) },
                onOpenPermissions = { push(Route.Permissions) },
            )

            Route.Audit -> AuditScreen(
                bottomBar = bottomBar,
                onOpenEntry = { id -> push(Route.AuditDetail(id)) },
                onOpenSettings = { push(Route.Settings) },
            )

            Route.Findings -> FindingsScreen(
                onBack = ::pop,
                onOpenFinding = { pkg -> push(Route.FindingDetail(pkg)) },
            )

            is Route.FindingDetail -> FindingDetailScreen(
                packageName = destination.packageName,
                onBack = ::pop,
            )

            Route.ThreatIntel -> ThreatIntelligenceScreen(onBack = ::pop)

            Route.NetworkGuard -> NetworkGuardScreen(onBack = ::pop)

            is Route.AuditDetail -> AuditEventDetailScreen(
                entryId = destination.entryId,
                onBack = ::pop,
            )

            Route.Settings -> SettingsScreen(
                onBack = ::pop,
                onOpen = { push(it) },
            )

            Route.Appearance -> AppearanceScreen(onBack = ::pop)
            Route.Protection -> ProtectionSettingsScreen(
                onBack = ::pop,
                onOpenPolicyCheck = { push(Route.PolicyCheck) },
                onOpenNetworkGuard = { push(Route.NetworkGuard) },
            )
            Route.Permissions -> PermissionsScreen(onBack = ::pop)
            Route.OnDeviceAi -> OnDeviceAiScreen(onBack = ::pop)
            Route.PrivacyData -> PrivacyDataScreen(onBack = ::pop)
            Route.About -> AboutScreen(onBack = ::pop)
            Route.Diagnostics -> DiagnosticsScreen(onBack = ::pop)
            Route.PolicyCheck -> PolicyCheckScreen(onBack = ::pop)
        }
    }
}
