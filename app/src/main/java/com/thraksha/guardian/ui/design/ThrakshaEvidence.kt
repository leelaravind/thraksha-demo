package com.thraksha.guardian.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The DECLARED → GRANTED → OBSERVED evidence rail.
 *
 * This component is deliberately dumb: it renders exactly the three stages it is given.
 * The caller derives them from `CapabilityEvidence`, which means an OBSERVED tick can
 * never appear unless Thraksha itself verified an observation — the rail has no way to
 * infer one stage from another.
 */

/** One stage of the progression. */
data class EvidenceStage(
    val name: String,
    /** Plain-language line shown to everyone. */
    val summary: String,
    val state: EvidenceStageState,
)

enum class EvidenceStageState {
    /** Confirmed true (declared in the manifest, granted by Android, observed by us). */
    CONFIRMED,

    /** Confirmed true *and* worth attention — a real observation of live behaviour. */
    CONFIRMED_NOTABLE,

    /** Confirmed false: not declared, not granted, not enabled. */
    ABSENT,

    /** Genuinely undeterminable, or Android exposes no signal. Never dressed up. */
    UNKNOWN,
}

@Composable
fun ThrakshaEvidenceRail(
    capability: String,
    stages: List<EvidenceStage>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.xs),
    ) {
        Text(
            text = capability,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        stages.forEachIndexed { index, stage ->
            ThrakshaEvidenceStageRow(
                stage = stage,
                showConnector = index != stages.lastIndex,
            )
        }
    }
}

@Composable
private fun ThrakshaEvidenceStageRow(
    stage: EvidenceStage,
    showConnector: Boolean,
) {
    val tone = when (stage.state) {
        EvidenceStageState.CONFIRMED -> StatusTone.OK
        EvidenceStageState.CONFIRMED_NOTABLE -> StatusTone.DANGER
        EvidenceStageState.ABSENT -> StatusTone.NEUTRAL
        EvidenceStageState.UNKNOWN -> StatusTone.NEUTRAL
    }
    val icon = when (stage.state) {
        EvidenceStageState.CONFIRMED -> Icons.Default.Check
        EvidenceStageState.CONFIRMED_NOTABLE -> Icons.Default.Visibility
        EvidenceStageState.ABSENT -> Icons.Default.Remove
        EvidenceStageState.UNKNOWN -> Icons.Default.HelpOutline
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
    ) {
        // Node + connector rail.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(tone.container()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.onContainer(),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showConnector) ThrakshaSpacing.sm else 0.dp),
        ) {
            Text(
                text = stage.name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = tone.color(),
            )
            Text(
                text = stage.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (!showConnector) Spacer(Modifier.height(ThrakshaSpacing.xs))
}
