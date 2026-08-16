package com.thraksha.guardian.ui.screens.automate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.ai.AiIntentInterpreter
import com.thraksha.guardian.ai.LocalModelRepository
import com.thraksha.guardian.ai.ModelPhase
import com.thraksha.guardian.ai.OnDeviceIntentModel
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.AutomationActionStatus
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.AutomationEngine
import com.thraksha.guardian.automation.AutomationIntent
import com.thraksha.guardian.automation.AutomationPlan
import com.thraksha.guardian.automation.PlannedAction
import com.thraksha.guardian.automation.RoutineType
import com.thraksha.guardian.automation.RunPhase
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaLoadingState
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaPanel
import com.thraksha.guardian.ui.design.ThrakshaPrimaryButton
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip
import com.thraksha.guardian.ui.design.ThrakshaTextAction
import kotlinx.coroutines.launch

/**
 * AUTOMATE — Ask Thraksha, plan preview, active routine, restoration.
 *
 * The safety boundary from Phase 9/10 is preserved exactly:
 *
 *   input → Understanding… → validated intent → **deterministic planner** preview →
 *   explicit START → ACTIVE → STOP & RESTORE → verified restoration
 *
 * Inference completing starts nothing. The model's own prose is never rendered: the
 * "Thraksha understood" line comes from the validated `AutomationIntent`, and every action
 * shown comes from `AutomationPlan`, which the planner — not the model — produces.
 */
@Composable
fun AutomateScreen(
    bottomBar: @Composable () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val modelState by LocalModelRepository.state.collectAsStateWithLifecycle()
    val engine by AutomationEngine.state.collectAsStateWithLifecycle()

    var request by rememberSaveable { mutableStateOf("") }
    var ask by remember { mutableStateOf<AskState>(AskState.Idle) }
    var preview by remember { mutableStateOf<PendingPlan?>(null) }

    // Integrity is verified once per process. "The file is present" is never treated as
    // "AI is ready" — only ModelPhase.LOADED is.
    LaunchedEffect(Unit) {
        if (modelState.phase == ModelPhase.MISSING || modelState.detail.isEmpty()) {
            LocalModelRepository.verify(context)
        }
    }

    fun openPreview(intent: AutomationIntent) {
        preview = PendingPlan(
            intent = intent,
            plan = AutomationEngine.preview(context, intent),
            understood = null,
        )
    }

    // ---- Plan preview is a focused, transactional screen ---------------------
    preview?.let { pending ->
        PlanPreviewScreen(
            pending = pending,
            onCancel = { preview = null },
            onStart = {
                preview = null
                ask = AskState.Idle
                request = ""
                scope.launch { AutomationEngine.start(context, pending.intent) }
            },
            onOpenPermissions = onOpenPermissions,
        )
        return
    }

    ThrakshaScaffold(
        title = "Automate",
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        ThrakshaPageColumn(padding = padding, modifier = Modifier.imePadding()) {
            when (engine.phase) {
                RunPhase.EXECUTING,
                RunPhase.ACTIVE,
                RunPhase.PARTIAL,
                RunPhase.RESTORING,
                RunPhase.RESTORED,
                RunPhase.RESTORE_FAILED,
                RunPhase.FAILED,
                -> RoutineStateSection(
                    onStop = { scope.launch { AutomationEngine.stop(context, "stopped by the user") } },
                    onAcknowledge = { AutomationEngine.acknowledgeResult() },
                )

                RunPhase.IDLE, RunPhase.PREFLIGHT -> {
                    AskThrakshaSection(
                        modelState = modelState,
                        request = request,
                        onRequestChange = { request = it },
                        ask = ask,
                        onAsk = {
                            keyboard?.hide()
                            val text = request
                            ask = AskState.Understanding
                            scope.launch {
                                if (!OnDeviceIntentModel.isLoaded) {
                                    val loaded = OnDeviceIntentModel.load(context)
                                    if (loaded.phase != ModelPhase.LOADED) {
                                        ask = AskState.Unavailable(loaded.detail)
                                        return@launch
                                    }
                                }
                                ask = when (
                                    val outcome = AiIntentInterpreter.interpret(context, text)
                                ) {
                                    is AiIntentInterpreter.Result.PlanReady -> {
                                        preview = PendingPlan(
                                            intent = outcome.intent,
                                            plan = outcome.plan,
                                            understood = outcome.understood,
                                        )
                                        AskState.Idle
                                    }

                                    is AiIntentInterpreter.Result.Clarify ->
                                        AskState.Clarify(outcome.question)

                                    is AiIntentInterpreter.Result.Unsupported ->
                                        AskState.Unsupported(outcome.message)

                                    is AiIntentInterpreter.Result.Rejected ->
                                        AskState.Rejected

                                    is AiIntentInterpreter.Result.ModelUnavailable ->
                                        AskState.Unavailable(outcome.message)
                                }
                            }
                        },
                        onCancel = {
                            OnDeviceIntentModel.cancel()
                            ask = AskState.Idle
                        },
                        onDismiss = { ask = AskState.Idle },
                        onOpenAi = onOpenAi,
                    )

                    RoutineChooser(onChoose = ::openPreview)

                    engine.message?.let { message ->
                        ThrakshaCard(ribbon = StatusTone.WARN) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            ThrakshaFootnote(
                "Thraksha records your current settings before a routine changes anything, " +
                    "and puts exactly those settings back when it ends. Nothing starts " +
                    "without you pressing START.",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ask Thraksha
// ---------------------------------------------------------------------------

/** UI-local view state. Never the source of automation truth. */
private sealed interface AskState {
    data object Idle : AskState
    data object Understanding : AskState
    data class Clarify(val question: String) : AskState
    data class Unsupported(val message: String) : AskState
    data object Rejected : AskState
    data class Unavailable(val message: String) : AskState
}

/** An intent whose deterministic plan is awaiting explicit confirmation. */
private data class PendingPlan(
    val intent: AutomationIntent,
    val plan: AutomationPlan,
    /** Present only when the request arrived through the model. */
    val understood: String?,
)

@Composable
private fun AskThrakshaSection(
    modelState: com.thraksha.guardian.ai.ModelState,
    request: String,
    onRequestChange: (String) -> Unit,
    ask: AskState,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val usable = modelState.phase == ModelPhase.LOADED || modelState.phase == ModelPhase.READY
    // An ask already in flight outranks the model phase. The first ask legitimately moves
    // the model READY → LOADING, and treating that as "unavailable" would erase the user's
    // request mid-flight and tell them nothing was happening.
    val asking = ask is AskState.Understanding

    ThrakshaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ask Thraksha",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThrakshaStatusChip(
                text = if (asking) {
                    "Working"
                } else {
                    when (modelState.phase) {
                        ModelPhase.LOADED -> "Ready"
                        ModelPhase.READY -> "Available"
                        ModelPhase.LOADING -> "Loading"
                        ModelPhase.VERIFYING -> "Checking"
                        ModelPhase.MISSING -> "Not installed"
                        ModelPhase.UNSUPPORTED -> "Not supported"
                        ModelPhase.ERROR -> "Unavailable"
                    }
                },
                tone = when {
                    asking -> StatusTone.WARN
                    modelState.phase == ModelPhase.LOADED ||
                        modelState.phase == ModelPhase.READY -> StatusTone.OK
                    modelState.phase == ModelPhase.LOADING ||
                        modelState.phase == ModelPhase.VERIFYING -> StatusTone.WARN
                    else -> StatusTone.NEUTRAL
                },
            )
        }
        Spacer(Modifier.height(ThrakshaSpacing.sm))

        if (asking) {
            // The whole card becomes the wait state: what was asked, and that it is being
            // worked out here on the phone.
            Text(
                text = "\"$request\"",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThrakshaLoadingState(
                message = "Understanding…",
                supporting = if (modelState.phase == ModelPhase.LOADING) {
                    "Getting the on-device model ready. This only happens once."
                } else {
                    "Working this out on your phone. Nothing is sent anywhere."
                },
            )
            ThrakshaTextAction("Cancel", onClick = onCancel)
            return@ThrakshaCard
        }

        if (!usable) {
            // Model-unavailable is a first-class state, not an error dialog: the
            // deterministic routines below are entirely unaffected.
            Text(
                text = modelState.detail.ifEmpty {
                    "On-device understanding is not available on this device right now."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ThrakshaSpacing.sm))
            Text(
                text = "You can still start Meeting, Focus and Driving below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ThrakshaSpacing.md))
            ThrakshaSecondaryButton(text = "About on-device AI", onClick = onOpenAi)
            return@ThrakshaCard
        }

        Text(
            text = "Say what you need in your own words. Thraksha shows you exactly what " +
                "will change before anything happens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ThrakshaSpacing.md))

        OutlinedTextField(
            value = request,
            onValueChange = onRequestChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = ask !is AskState.Understanding,
            placeholder = { Text("e.g. I have a meeting for 45 minutes") },
            minLines = 2,
            maxLines = 4,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (request.isNotBlank()) onAsk() }),
            trailingIcon = {
                IconButton(
                    onClick = onAsk,
                    enabled = request.isNotBlank() && ask !is AskState.Understanding,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask Thraksha")
                }
            },
        )

        when (ask) {
            // Handled above: an in-flight ask replaces the input entirely.
            is AskState.Understanding -> Unit

            is AskState.Clarify -> ResponseNote(
                label = "One more thing",
                tone = StatusTone.WARN,
                message = ask.question,
                onDismiss = onDismiss,
            )

            is AskState.Unsupported -> ResponseNote(
                label = "Not something Thraksha can automate",
                tone = StatusTone.WARN,
                message = ask.message,
                onDismiss = onDismiss,
            )

            AskState.Rejected -> ResponseNote(
                label = "Could not use that request",
                tone = StatusTone.DANGER,
                message = "Thraksha could not turn that into a routine it is allowed to " +
                    "run, so it did nothing. Try describing a Meeting, Focus or Driving " +
                    "routine instead.",
                onDismiss = onDismiss,
            )

            is AskState.Unavailable -> ResponseNote(
                label = "On-device AI unavailable",
                tone = StatusTone.NEUTRAL,
                message = ask.message + " The routines below still work.",
                onDismiss = onDismiss,
            )

            AskState.Idle -> Unit
        }
    }
}

@Composable
private fun ResponseNote(
    label: String,
    tone: StatusTone,
    message: String,
    onDismiss: () -> Unit,
) {
    Spacer(Modifier.height(ThrakshaSpacing.md))
    ThrakshaPanel {
        ThrakshaStatusChip(label, tone)
        Spacer(Modifier.height(ThrakshaSpacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ThrakshaTextAction("Dismiss", onClick = onDismiss)
    }
}

// ---------------------------------------------------------------------------
// Routines
// ---------------------------------------------------------------------------

private data class RoutineSpec(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val intent: AutomationIntent,
)

/**
 * The routines Thraksha actually implements. Meeting, Focus and Driving — plus the custom
 * builder that proves the engine is composable. Nothing else is offered, and none of these
 * can start from this list: each one opens the plan preview first.
 */
@Composable
private fun RoutineChooser(onChoose: (AutomationIntent) -> Unit) {
    val routines = listOf(
        RoutineSpec(
            label = "Meeting",
            description = "Quiet for 30 minutes.",
            icon = Icons.Default.Groups,
            intent = AutomationIntent(RoutineType.MEETING, durationMinutes = 30),
        ),
        RoutineSpec(
            label = "Focus",
            description = "Fewer interruptions for an hour.",
            icon = Icons.Default.SelfImprovement,
            intent = AutomationIntent(RoutineType.FOCUS, durationMinutes = 60),
        ),
        RoutineSpec(
            label = "Driving",
            description = "Runs until you stop it.",
            icon = Icons.Default.DirectionsCar,
            intent = AutomationIntent(RoutineType.DRIVING),
        ),
    )

    Column {
        ThrakshaSectionHeader("Routines")
        Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap)) {
            routines.forEach { routine ->
                ThrakshaCard(onClick = { onChoose(routine.intent) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            routine.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routine.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = routine.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ThrakshaStatusChip("Preview", StatusTone.NEUTRAL)
                    }
                }
            }

            ThrakshaCard {
                ThrakshaDisclosure("Build a custom routine") {
                    Text(
                        text = "Do Not Disturb, half brightness and a two-minute screen " +
                            "timeout, for 15 minutes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ThrakshaSecondaryButton(
                        text = "Preview custom routine",
                        icon = Icons.Default.Tune,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onChoose(CustomRoutine.intent) },
                    )
                }
            }
        }
    }
}

private object CustomRoutine {
    val intent = AutomationIntent(
        routineType = RoutineType.CUSTOM,
        durationMinutes = 15,
        customActions = listOf(
            com.thraksha.guardian.automation.ActionTarget(
                AutomationCapability.DO_NOT_DISTURB,
                com.thraksha.guardian.automation.AutomationTargets.DND_PRIORITY,
            ),
            com.thraksha.guardian.automation.ActionTarget(
                AutomationCapability.SCREEN_BRIGHTNESS,
                128,
            ),
            com.thraksha.guardian.automation.ActionTarget(
                AutomationCapability.SCREEN_TIMEOUT,
                120_000,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// Plan preview
// ---------------------------------------------------------------------------

/**
 * PLAN PREVIEW — the last screen before anything changes.
 *
 * Transactional, so the navigation shell is suppressed (Guardian Prime's rule for focused
 * flows) and the only ways out are Cancel and START. Every row is a `PlannedAction` from
 * the deterministic planner.
 */
@Composable
private fun PlanPreviewScreen(
    pending: PendingPlan,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val plan = pending.plan

    // The preview is rendered in place of the Automate root rather than pushed onto the
    // tab's back stack, so system back has to be claimed here — otherwise it would fall
    // through to the shell and leave the app while a plan was awaiting confirmation.
    BackHandler(enabled = true) { onCancel() }

    ThrakshaScaffold(
        title = "Plan preview",
        actions = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        },
    ) { padding ->
        ThrakshaPageColumn(padding = padding) {
            pending.understood?.let { understood ->
                ThrakshaCard(ribbon = StatusTone.OK) {
                    ThrakshaStatusChip("Thraksha understood", StatusTone.OK)
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    // App-authored from the validated intent — never model prose.
                    Text(
                        text = understood,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            ThrakshaCard {
                Text(
                    text = plan.routineLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = plan.durationMinutes?.let { "For $it minutes" } ?: "Until you stop it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ThrakshaSpacing.lg))
                ThrakshaSectionHeader(
                    "${plan.actions.size} " + if (plan.actions.size == 1) "change" else "changes",
                )
                Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm)) {
                    plan.actions.forEach { action -> PlannedActionRow(action) }
                }

                if (!plan.executable) {
                    Spacer(Modifier.height(ThrakshaSpacing.lg))
                    ThrakshaPanel {
                        ThrakshaStatusChip("Cannot run yet", StatusTone.DANGER)
                        Spacer(Modifier.height(ThrakshaSpacing.xs))
                        Text(
                            text = plan.blockedReason ?: "This routine cannot run right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ThrakshaTextAction("Open permissions", onClick = onOpenPermissions)
                    }
                } else if (plan.actions.any { it.support == ActionSupport.USER_ACTION_REQUIRED }) {
                    Spacer(Modifier.height(ThrakshaSpacing.lg))
                    ThrakshaPanel {
                        ThrakshaStatusChip("Some steps need you", StatusTone.WARN)
                        Spacer(Modifier.height(ThrakshaSpacing.xs))
                        Text(
                            text = "Thraksha will apply what it can and tell you which " +
                                "steps you have to finish yourself.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ThrakshaTextAction("Grant access", onClick = onOpenPermissions)
                    }
                }

                Spacer(Modifier.height(ThrakshaSpacing.lg))
                Text(
                    text = "Your current settings are saved first and restored exactly when " +
                        "the routine ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The single, explicit start. This is the only path into the engine.
            if (plan.executable) {
                ThrakshaPrimaryButton(
                    text = "START",
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStart,
                )
            }
            ThrakshaSecondaryButton(
                text = "Cancel",
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun PlannedActionRow(action: PlannedAction) {
    // Chip labels stay short: a long chip steals width from the action name, which is
    // the part the user actually needs to read.
    val (label, tone) = when (action.support) {
        ActionSupport.SUPPORTED -> "Ready" to StatusTone.OK
        ActionSupport.USER_ACTION_REQUIRED -> "Needs you" to StatusTone.WARN
        ActionSupport.UNSUPPORTED -> "Not possible" to StatusTone.NEUTRAL
    }
    ThrakshaPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ThrakshaStatusChip(label, tone, modifier = Modifier.wrapContentWidth())
        }
        if (action.support != ActionSupport.SUPPORTED) {
            Text(
                text = action.supportDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Active routine and restoration
// ---------------------------------------------------------------------------

/**
 * Everything after START. Each phase renders the engine's own results — an action that the
 * engine classified as OPENED is never shown as verified.
 */
@Composable
private fun RoutineStateSection(
    onStop: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val engine by AutomationEngine.state.collectAsStateWithLifecycle()
    val run = engine.activeRun

    when (engine.phase) {
        RunPhase.EXECUTING -> ThrakshaCard(ribbon = StatusTone.WARN) {
            ThrakshaLoadingState(
                message = "Starting ${engine.plan?.routineLabel ?: "routine"}…",
                supporting = "Each change is checked against your phone's real settings.",
            )
        }

        RunPhase.ACTIVE, RunPhase.PARTIAL -> {
            val partial = engine.phase == RunPhase.PARTIAL
            ThrakshaCard(ribbon = if (partial) StatusTone.WARN else StatusTone.OK) {
                ThrakshaStatusChip(
                    if (partial) "Partly active" else "Active",
                    if (partial) StatusTone.WARN else StatusTone.OK,
                )
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = "${run?.routineLabel ?: "Routine"} is running",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = run?.expiresAt?.let { expiry ->
                        val minutes = ((expiry - System.currentTimeMillis()) / 60_000)
                            .coerceAtLeast(0)
                        "Ends in about $minutes min, then your settings come back automatically."
                    } ?: "Runs until you stop it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(ThrakshaSpacing.lg))
                ThrakshaSectionHeader("What changed")
                Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm)) {
                    engine.actionResults.forEach { result ->
                        val (label, tone) = when (result.status) {
                            // "Verified" and "Opened" are deliberately distinct: an app
                            // launch is OPENED, and must never read as a verified change.
                            AutomationActionStatus.ACTED -> "Verified" to StatusTone.OK
                            AutomationActionStatus.OPENED -> "Opened" to StatusTone.OK
                            AutomationActionStatus.USER_ACTION_REQUIRED ->
                                "Needs you" to StatusTone.WARN
                            AutomationActionStatus.FAILED -> "Failed" to StatusTone.DANGER
                            AutomationActionStatus.UNSUPPORTED ->
                                "Not possible" to StatusTone.NEUTRAL
                            else -> result.status.name.replace('_', ' ').lowercase() to
                                StatusTone.NEUTRAL
                        }
                        ThrakshaPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = result.action.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                ThrakshaStatusChip(label, tone)
                            }
                            if (result.status != AutomationActionStatus.ACTED) {
                                Text(
                                    text = result.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(ThrakshaSpacing.lg))
                ThrakshaPrimaryButton(
                    text = "STOP & RESTORE",
                    icon = Icons.Default.StopCircle,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStop,
                )
            }
        }

        RunPhase.RESTORING -> ThrakshaCard(ribbon = StatusTone.WARN) {
            ThrakshaLoadingState(
                message = "Restoring your settings…",
                supporting = "Putting back exactly what was saved before the routine started.",
            )
        }

        RunPhase.RESTORED, RunPhase.RESTORE_FAILED, RunPhase.FAILED -> {
            val ok = engine.phase == RunPhase.RESTORED
            val partial = engine.phase == RunPhase.RESTORE_FAILED
            ThrakshaCard(ribbon = if (ok) StatusTone.OK else StatusTone.DANGER) {
                ThrakshaStatusChip(
                    when {
                        ok -> "Restored"
                        partial -> "Partly restored"
                        else -> "Did not run"
                    },
                    if (ok) StatusTone.OK else StatusTone.DANGER,
                )
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = when {
                        ok -> "Your previous settings are back"
                        partial -> "Some settings could not be put back"
                        else -> "The routine did not run"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                engine.message?.let { message ->
                    Spacer(Modifier.height(ThrakshaSpacing.xs))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (engine.restoreResults.isNotEmpty()) {
                    Spacer(Modifier.height(ThrakshaSpacing.lg))
                    ThrakshaDisclosure("Restore details") {
                        engine.restoreResults.forEach { result ->
                            val restored =
                                result.status == AutomationActionStatus.RESTORED ||
                                    result.status == AutomationActionStatus.ROLLED_BACK
                            ThrakshaFactRow(
                                label = result.capability.name.replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                value = result.status.name.replace('_', ' ').lowercase(),
                                valueTone = if (restored) StatusTone.OK else StatusTone.DANGER,
                            )
                            Text(
                                text = result.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(ThrakshaSpacing.lg))
                ThrakshaPrimaryButton(
                    text = "Done",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAcknowledge,
                )
            }
        }

        RunPhase.IDLE, RunPhase.PREFLIGHT -> Unit
    }
}
