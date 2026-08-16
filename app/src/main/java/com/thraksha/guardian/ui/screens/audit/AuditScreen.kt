package com.thraksha.guardian.ui.screens.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.data.db.AuditEntity
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.FoundationStatus
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaEmptyState
import com.thraksha.guardian.ui.design.ThrakshaErrorState
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip
import com.thraksha.guardian.ui.design.color
import com.thraksha.guardian.ui.design.container
import kotlinx.coroutines.flow.emptyFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AUDIT — the hash-chained trail as a human timeline.
 *
 * Rows come straight from the encrypted `audit_log`; the screen has no cache and cannot
 * add, merge or reorder entries. Chain internals (ids, tiers, hashes) live in the detail
 * view's technical section, not here.
 */
@Composable
fun AuditScreen(
    bottomBar: @Composable () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val foundation by FoundationStatus.state.collectAsStateWithLifecycle()

    var limit by rememberSaveable { mutableIntStateOf(50) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    val dao = remember { runCatching { DatabaseProvider.get(context).auditDao() }.getOrNull() }
    val entries by (dao?.observeRecent(limit) ?: emptyFlow())
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val selectedCategory = filter?.let { AuditCategory.valueOf(it) }
    val visible = entries.filter { entry ->
        selectedCategory == null || AuditPresentation.of(entry).category == selectedCategory
    }

    ThrakshaScaffold(
        title = "Audit",
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        ThrakshaPageColumn(padding = padding) {
            if (dao == null || foundation is FoundationStatus.State.Failed) {
                ThrakshaErrorState(
                    title = "Audit trail unavailable",
                    message = (foundation as? FoundationStatus.State.Failed)?.message
                        ?: "The encrypted store could not be opened, so nothing is being " +
                        "recorded right now.",
                    icon = Icons.Default.History,
                )
                return@ThrakshaPageColumn
            }

            Text(
                text = "Everything Thraksha did on this phone, in order.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- Category filter ---------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
            ) {
                CategoryChip("All", filter == null) { filter = null }
                AuditCategory.entries.forEach { category ->
                    CategoryChip(category.label, filter == category.name) {
                        filter = if (filter == category.name) null else category.name
                    }
                }
            }

            if (visible.isEmpty()) {
                ThrakshaEmptyState(
                    title = if (entries.isEmpty()) "Nothing recorded yet" else "Nothing in this category",
                    message = if (entries.isEmpty()) {
                        "Run a scan or start a routine — every step Thraksha takes is " +
                            "recorded here."
                    } else {
                        "Try a different category."
                    },
                    icon = Icons.Default.History,
                )
            } else {
                // ---- Timeline, grouped by day --------------------------------
                var lastDay: String? = null
                Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap)) {
                    visible.forEach { entry ->
                        val day = dayLabel(entry.timestamp)
                        if (day != lastDay) {
                            lastDay = day
                            Spacer(Modifier.height(ThrakshaSpacing.xs))
                            ThrakshaSectionHeader(day)
                        }
                        TimelineRow(entry) { onOpenEntry(entry.id) }
                    }
                }

                if (entries.size >= limit) {
                    ThrakshaSecondaryButton(
                        text = "Load older events",
                        icon = Icons.Default.History,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { limit += 50 },
                    )
                }
            }

            ThrakshaFootnote(
                "Each entry is chained to the one before it, so a missing or altered " +
                    "record can be detected. The trail never leaves this phone.",
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun TimelineRow(entry: AuditEntity, onClick: () -> Unit) {
    val presentation = AuditPresentation.of(entry)
    ThrakshaCard(ribbon = presentation.tone, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(presentation.tone.container()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (presentation.category) {
                        AuditCategory.SECURITY -> Icons.Default.Shield
                        AuditCategory.AUTOMATION -> Icons.Default.Bolt
                        AuditCategory.AI -> Icons.Default.Psychology
                        AuditCategory.USER -> Icons.Default.Person
                    },
                    contentDescription = null,
                    tint = presentation.tone.color(),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = presentation.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(ThrakshaSpacing.sm))
                    Text(
                        text = clock(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = presentation.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(ThrakshaSpacing.sm))
                ThrakshaStatusChip(presentation.category.label, presentation.tone)
            }
        }
    }
}

internal fun clock(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

internal fun fullTimestamp(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

/** "Today" / "Yesterday" / a date, for the timeline's day separators. */
internal fun dayLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val today = startOfDay(now)
    val day = startOfDay(epochMillis)
    val dayMillis = 24L * 60 * 60 * 1000
    return when (today - day) {
        0L -> "Today"
        dayMillis -> "Yesterday"
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(epochMillis))
    }
}
