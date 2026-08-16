package com.thraksha.guardian.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.ai.LocalModelRepository
import com.thraksha.guardian.ai.ModelPhase
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip
import kotlinx.coroutines.launch

/**
 * ON-DEVICE AI — the model's real lifecycle state.
 *
 * The consumer line is one sentence; everything technical is collapsed. The Stitch
 * design's "secure enclave", "NPU active" and invented model name are not reproduced —
 * inference runs in the app process on a pinned local model file, and that is what the
 * technical section says.
 */
@Composable
fun OnDeviceAiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by LocalModelRepository.state.collectAsStateWithLifecycle()

    val tone = when (state.phase) {
        ModelPhase.LOADED, ModelPhase.READY -> StatusTone.OK
        ModelPhase.LOADING, ModelPhase.VERIFYING -> StatusTone.WARN
        ModelPhase.MISSING, ModelPhase.UNSUPPORTED -> StatusTone.NEUTRAL
        ModelPhase.ERROR -> StatusTone.DANGER
    }
    val headline = when (state.phase) {
        ModelPhase.LOADED -> "Local AI ready"
        ModelPhase.READY -> "Local AI available"
        ModelPhase.LOADING -> "Loading the model…"
        ModelPhase.VERIFYING -> "Checking the model…"
        ModelPhase.MISSING -> "Model not installed"
        ModelPhase.UNSUPPORTED -> "Not supported on this device"
        ModelPhase.ERROR -> "Local AI unavailable"
    }

    ThrakshaScaffold(title = "On-device AI", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            ThrakshaCard(ribbon = tone) {
                ThrakshaStatusChip(
                    when (state.phase) {
                        ModelPhase.LOADED -> "Ready"
                        ModelPhase.READY -> "Available"
                        ModelPhase.LOADING -> "Loading"
                        ModelPhase.VERIFYING -> "Checking"
                        ModelPhase.MISSING -> "Not installed"
                        ModelPhase.UNSUPPORTED -> "Unsupported"
                        ModelPhase.ERROR -> "Unavailable"
                    },
                    tone,
                )
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = "When you ask Thraksha for a routine in your own words, the " +
                        "model works it out on this phone. Nothing about your request is " +
                        "sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.detail.isNotEmpty()) {
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Text(
                        text = state.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(ThrakshaSpacing.lg))
                ThrakshaSecondaryButton(
                    text = "Check the model again",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { scope.launch { LocalModelRepository.verify(context) } },
                )

                Spacer(Modifier.height(ThrakshaSpacing.md))
                ThrakshaDisclosure("Technical details") {
                    ThrakshaFactRow("Lifecycle phase", state.phase.name)
                    ThrakshaFactRow("Runtime", "LiteRT-LM (on-device)")
                    ThrakshaFactRow("Execution", "In this app's process, offline")
                    ThrakshaFactRow(
                        "Integrity gate",
                        "Size, container magic and SHA-256 checked before load",
                    )
                    state.verifyMillis?.let {
                        ThrakshaFactRow("Last integrity check", "${it} ms")
                    }
                    state.loadMillis?.let {
                        ThrakshaFactRow("Last load", "${it} ms")
                    }
                    ThrakshaFactRow(
                        "Readiness rule",
                        "Only the LOADED phase counts as ready",
                    )
                }
            }

            ThrakshaCard {
                Text(
                    text = "What the model is allowed to do",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = "The model only proposes a routine. It cannot change a setting, " +
                        "run a command or make a security decision. Thraksha checks its " +
                        "output against a fixed set of allowed routines, builds the plan " +
                        "itself, and waits for you to press START.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ThrakshaSpacing.sm))
                Text(
                    text = "If the model is missing or fails to load, Meeting, Focus and " +
                        "Driving keep working exactly as before.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ThrakshaFootnote(
                "Working this out on the phone takes about ten seconds. That time is the " +
                    "model running locally instead of a server answering.",
            )
        }
    }
}
