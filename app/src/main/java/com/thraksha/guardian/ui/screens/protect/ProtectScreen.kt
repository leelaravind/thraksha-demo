package com.thraksha.guardian.ui.screens.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.automation.AutomationEngine
import com.thraksha.guardian.security.FoundationStatus
import com.thraksha.guardian.security.act.UserActCoordinator
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.security.scan.ScanProgress
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaAttentionCard
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaHeroRing
import com.thraksha.guardian.ui.design.ThrakshaLoadingState
import com.thraksha.guardian.ui.design.ThrakshaModuleCard
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaPrimaryButton
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import kotlinx.coroutines.launch

/**
 * PROTECT — the primary surface.
 *
 * Answers the three questions the guide asks a main surface to answer: what is happening
 * (hero), does anything need attention (attention card), and what can I do (Scan Device
 * plus four module tiles that open their own detail).
 *
 * No evidence, hash, permission constant or engine term appears here. Every value is read
 * from a live engine flow; there is no local mirror of any of it.
 */
@Composable
fun ProtectScreen(
    bottomBar: @Composable () -> Unit,
    onOpenFindings: () -> Unit,
    onOpenFinding: (String) -> Unit,
    onOpenThreatIntel: () -> Unit,
    onOpenNetworkGuard: () -> Unit,
    onOpenAutomate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val foundation by FoundationStatus.state.collectAsStateWithLifecycle()
    val progress by DeviceScanEngine.progress.collectAsStateWithLifecycle()
    val result by DeviceScanEngine.lastResult.collectAsStateWithLifecycle()
    val guard by NetworkGuard.state.collectAsStateWithLifecycle()
    val automation by AutomationEngine.state.collectAsStateWithLifecycle()
    val dismissed by UserActCoordinator.dismissedPackages.collectAsStateWithLifecycle()

    val scanning = progress is ScanProgress.Discovering || progress is ScanProgress.Analyzing
    val attention = ProtectPresentation.attentionItems(result, dismissed)
    val threatCount = attention.count {
        it.displayStatus == com.thraksha.guardian.security.evidence.AppDisplayStatus.KNOWN_THREAT_MATCH
    }
    val summary = ProtectPresentation.summary(
        foundation = foundation,
        result = result,
        scanning = scanning,
        attentionCount = attention.size,
        threatCount = threatCount,
    )

    ThrakshaScaffold(
        title = "Thraksha Guardian",
        showBrand = true,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        ThrakshaPageColumn(padding = padding) {
            // ---- Hero -------------------------------------------------------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(ThrakshaSpacing.sm))
                ThrakshaHeroRing(
                    // A neutral state (no scan yet, starting up) must not wear a warning
                    // triangle — that would imply a problem the app has not found.
                    icon = when (summary.tone) {
                        StatusTone.OK, StatusTone.NEUTRAL -> Icons.Default.Shield
                        StatusTone.WARN -> Icons.Default.WarningAmber
                        StatusTone.DANGER -> Icons.Default.GppMaybe
                    },
                    label = summary.state,
                    tone = summary.tone,
                    animated = scanning || summary.tone == StatusTone.OK,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xl))
                Text(
                    text = summary.headline,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = summary.supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = ThrakshaSpacing.lg),
                )
                Spacer(Modifier.height(ThrakshaSpacing.xl))

                if (scanning) {
                    ScanProgressBlock(progress)
                } else {
                    ThrakshaPrimaryButton(
                        text = "Scan Device",
                        icon = Icons.Default.Radar,
                        enabled = foundation is FoundationStatus.State.Ready,
                        onClick = { scope.launch { DeviceScanEngine.scanDevice(context) } },
                    )
                }
            }

            // ---- Attention --------------------------------------------------
            if (attention.isNotEmpty()) {
                val worst = attention.first()
                ThrakshaAttentionCard(
                    title = ProtectPresentation.plural(
                        attention.size,
                        "item needs your attention",
                        "items need your attention",
                    ),
                    message = "${worst.displayName} — ${ProtectPresentation.plainSummary(worst)}",
                    icon = Icons.Default.WarningAmber,
                    tone = if (threatCount > 0) StatusTone.DANGER else StatusTone.WARN,
                    actionLabel = if (attention.size == 1) "Review" else "Review all",
                    onAction = {
                        if (attention.size == 1) onOpenFinding(worst.packageName) else onOpenFindings()
                    },
                )
            }

            // ---- Modules ----------------------------------------------------
            val intel = ProtectPresentation.threatIntelligence(result)
            val network = ProtectPresentation.networkGuard(guard)
            val scanner = ProtectPresentation.appScanner(result, attention.size)
            val auto = ProtectPresentation.automation(
                automation.phase,
                automation.activeRun?.routineLabel ?: automation.plan?.routineLabel,
            )

            ModuleGrid(
                listOf(
                    ModuleSpec("Threat Intelligence", intel, Icons.Default.Language, onOpenThreatIntel),
                    ModuleSpec("Network Guard", network, Icons.Default.VpnLock, onOpenNetworkGuard),
                    ModuleSpec(
                        "App Scanner",
                        scanner,
                        Icons.Default.Apps,
                        if (result != null) onOpenFindings else null,
                    ),
                    ModuleSpec("Automation", auto, Icons.Default.Bolt, onOpenAutomate),
                ),
            )

            ThrakshaFootnote(
                "Everything here is checked on this phone. Nothing about your apps, " +
                    "network or routines leaves the device.",
            )
        }
    }
}

/**
 * Live scan feedback.
 *
 * The determinate bar appears only in the analysis stage, where `analyzed / total` is a
 * real fraction. Discovery has no denominator, so it stays indeterminate rather than
 * inventing one.
 */
@Composable
private fun ScanProgressBlock(progress: ScanProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
    ) {
        when (val p = progress) {
            is ScanProgress.Analyzing -> ThrakshaLoadingState(
                message = "Checking app ${p.analyzed + 1} of ${p.total}",
                supporting = "Reading what each app is allowed to do.",
                progress = if (p.total == 0) 0f else p.analyzed.toFloat() / p.total,
            )

            else -> ThrakshaLoadingState(
                message = "Finding installed apps…",
                supporting = "This takes a few seconds.",
            )
        }
        ThrakshaSecondaryButton(
            text = "Cancel scan",
            onClick = { DeviceScanEngine.requestCancel() },
        )
    }
}

/** One dashboard tile's fully-resolved content. */
private data class ModuleSpec(
    val title: String,
    val summary: ModuleSummary,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: (() -> Unit)?,
)

/**
 * Module tiles: one column on a phone, two from 600 dp so wide screens and landscape do
 * not stretch a tile across the whole width. Nothing is truncated in either layout — the
 * page scrolls instead.
 */
@Composable
private fun ModuleGrid(modules: List<ModuleSpec>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap)) {
            modules.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap),
                ) {
                    row.forEach { module ->
                        ThrakshaModuleCard(
                            title = module.title,
                            supporting = module.summary.supporting,
                            icon = module.icon,
                            status = module.summary.status,
                            tone = module.summary.tone,
                            onClick = module.onClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
