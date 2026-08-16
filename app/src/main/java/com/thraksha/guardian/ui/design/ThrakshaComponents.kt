package com.thraksha.guardian.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset

/**
 * Shared Compose primitives for Phase 11B.
 *
 * Every screen is assembled from these. Nothing here knows about scanners, automation or
 * audit chains — they take already-resolved presentation values, which keeps the "real
 * state only" rule enforceable at the call sites rather than buried in a widget.
 */

// ---------------------------------------------------------------------------
// Status vocabulary
// ---------------------------------------------------------------------------

/** How a piece of state should read. Chosen by the caller from real engine state. */
enum class StatusTone { OK, WARN, DANGER, NEUTRAL }

@Composable
internal fun StatusTone.color(): Color = when (this) {
    StatusTone.OK -> ThrakshaTheme.status.ok
    StatusTone.WARN -> ThrakshaTheme.status.warn
    StatusTone.DANGER -> ThrakshaTheme.status.danger
    StatusTone.NEUTRAL -> ThrakshaTheme.status.neutral
}

@Composable
internal fun StatusTone.container(): Color = when (this) {
    StatusTone.OK -> ThrakshaTheme.status.okContainer
    StatusTone.WARN -> ThrakshaTheme.status.warnContainer
    StatusTone.DANGER -> ThrakshaTheme.status.dangerContainer
    StatusTone.NEUTRAL -> ThrakshaTheme.status.neutralContainer
}

@Composable
internal fun StatusTone.onContainer(): Color = when (this) {
    StatusTone.OK -> ThrakshaTheme.status.onOkContainer
    StatusTone.WARN -> ThrakshaTheme.status.onWarnContainer
    StatusTone.DANGER -> ThrakshaTheme.status.onDangerContainer
    StatusTone.NEUTRAL -> ThrakshaTheme.status.onNeutralContainer
}

/**
 * Mono uppercase status pill. The text always names the state, so the chip is legible
 * without colour perception.
 */
@Composable
fun ThrakshaStatusChip(
    text: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = tone.onContainer(),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(ThrakshaRadius.sm))
            .background(tone.container())
            .padding(horizontal = ThrakshaSpacing.sm, vertical = ThrakshaSpacing.xs),
    )
}

// ---------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------

/**
 * The standard content container: 24 dp radius, Level-1 fill, hairline edge and an
 * optional 4 px severity ribbon down the leading edge (Guardian Prime "status ribbon"
 * rather than tinting the whole card).
 */
@Composable
fun ThrakshaCard(
    modifier: Modifier = Modifier,
    ribbon: StatusTone? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(ThrakshaSpacing.containerPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ThrakshaRadius.card)
    val ribbonColor = ribbon?.color()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ThrakshaTheme.status.card)
            // Drawn rather than laid out: the strip must span the card's final height,
            // which a sibling in a wrap-content Box cannot know.
            .then(
                if (ribbonColor != null) {
                    Modifier.drawBehind {
                        drawRect(
                            color = ribbonColor,
                            size = Size(4.dp.toPx(), size.height),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .border(1.dp, ThrakshaTheme.status.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** A tonal sub-panel used inside a card for nested detail. */
@Composable
fun ThrakshaPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ThrakshaRadius.panel))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(ThrakshaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.xs),
        content = content,
    )
}

/** Mono uppercase section label. Announced as a heading to screen readers. */
@Composable
fun ThrakshaSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = ThrakshaSpacing.xs, bottom = ThrakshaSpacing.xs)
            .semantics { heading() },
    )
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
fun ThrakshaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ThrakshaSpacing.touchTarget),
        shape = RoundedCornerShape(ThrakshaRadius.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = ThrakshaSpacing.xxl,
            vertical = ThrakshaSpacing.md,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(ThrakshaSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ThrakshaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tone: StatusTone? = null,
) {
    val content = tone?.color() ?: MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ThrakshaSpacing.touchTarget),
        shape = RoundedCornerShape(ThrakshaRadius.pill),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = ThrakshaSpacing.xl,
            vertical = ThrakshaSpacing.md,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(ThrakshaSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A filled pill in a severity tone, for in-card actions such as "Review". */
@Composable
fun ThrakshaToneButton(
    text: String,
    tone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ThrakshaSpacing.touchTarget),
        shape = RoundedCornerShape(ThrakshaRadius.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = tone.container(),
            contentColor = tone.onContainer(),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = ThrakshaSpacing.xl,
            vertical = ThrakshaSpacing.sm,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/** Settings-style navigation row: icon chip, label, optional value, chevron. */
@Composable
fun ThrakshaNavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    trailing: String? = null,
    trailingTone: StatusTone? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = ThrakshaSpacing.touchTarget)
            .padding(horizontal = ThrakshaSpacing.lg, vertical = ThrakshaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // The label owns the full width and the status sits beneath it. Competing for the
        // same line makes short labels like "Appearance" wrap mid-word once the chip is
        // wide enough, which looks broken at any font scale.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                ThrakshaStatusChip(trailing, trailingTone ?: StatusTone.NEUTRAL)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Label/value pair. Used for technical facts inside disclosure sections, so the value is
 * rendered in the mono technical face.
 */
@Composable
fun ThrakshaFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueTone: StatusTone? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = valueTone?.color() ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ThrakshaDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ---------------------------------------------------------------------------
// Progressive disclosure
// ---------------------------------------------------------------------------

/**
 * "Details on demand": a mono uppercase toggle that reveals technical content.
 *
 * Collapsed by default everywhere. This is the only sanctioned way to expose permission
 * constants, hashes, rule IDs and engine terminology.
 */
@Composable
fun ThrakshaDisclosure(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ThrakshaRadius.sm))
                .clickable { expanded = !expanded }
                .heightIn(min = ThrakshaSpacing.touchTarget)
                .padding(vertical = ThrakshaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
                content = content,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

/**
 * The Protect hero: a slow dashed outer ring, a static inner arc, and a centred icon with
 * a mono state label.
 *
 * The rings are decorative — they encode no measurement, so nothing here can imply a
 * "security score". [label] and [tone] come from real state.
 */
@Composable
fun ThrakshaHeroRing(
    icon: ImageVector,
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "hero-ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hero-ring-angle",
    )
    val accent = tone.color()
    val track = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .size(176.dp)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(if (animated) angle else 0f),
        ) {
            drawCircle(
                color = track,
                radius = size.minDimension / 2f - 4.dp.toPx(),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(20.dp.toPx(), 10.dp.toPx()),
                    ),
                ),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 18.dp.toPx()
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - inset * 2,
                    size.height - inset * 2,
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(ThrakshaSpacing.sm))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A compact status tile: icon chip, status chip, title, one supporting line.
 * The workhorse of the Protect dashboard.
 */
@Composable
fun ThrakshaModuleCard(
    title: String,
    supporting: String,
    icon: ImageVector,
    status: String,
    tone: StatusTone,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ThrakshaCard(
        modifier = modifier,
        ribbon = tone,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ThrakshaSpacing.xl,
            top = ThrakshaSpacing.lg,
            end = ThrakshaSpacing.lg,
            bottom = ThrakshaSpacing.lg,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            ThrakshaStatusChip(status, tone)
        }
        Spacer(Modifier.height(ThrakshaSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Empty / loading / error
// ---------------------------------------------------------------------------

@Composable
fun ThrakshaEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ThrakshaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(ThrakshaSpacing.xs))
            action()
        }
    }
}

@Composable
fun ThrakshaLoadingState(
    message: String,
    modifier: Modifier = Modifier,
    /** Real 0..1 fraction, or null when the underlying work has no denominator. */
    progress: Float? = null,
    supporting: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ThrakshaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
    ) {
        if (progress == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ThrakshaRadius.pill)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
fun ThrakshaErrorState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    ThrakshaCard(modifier = modifier, ribbon = StatusTone.DANGER) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ThrakshaTheme.status.dangerContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ThrakshaTheme.status.onDangerContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (action != null) {
                    Spacer(Modifier.height(ThrakshaSpacing.xs))
                    action()
                }
            }
        }
    }
}

/**
 * The attention banner on Protect: what needs a human, in one sentence, with one action.
 */
@Composable
fun ThrakshaAttentionCard(
    title: String,
    message: String,
    icon: ImageVector,
    tone: StatusTone,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ThrakshaCard(modifier = modifier, ribbon = tone) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tone.container()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tone.onContainer(),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(ThrakshaSpacing.md))
            ThrakshaToneButton(
                text = actionLabel,
                tone = tone,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Plain paragraph used for the honest explanatory footnotes each surface carries. */
@Composable
fun ThrakshaFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = ThrakshaSpacing.xs),
    )
}

/** Inline text action, e.g. "Dismiss". */
@Composable
fun ThrakshaTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ThrakshaSpacing.touchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Horizontal group of equal-width actions that stays legible on narrow screens. */
@Composable
fun ThrakshaActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
