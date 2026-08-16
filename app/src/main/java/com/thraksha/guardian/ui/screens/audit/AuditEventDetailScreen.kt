package com.thraksha.guardian.ui.screens.audit

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.thraksha.guardian.data.db.AuditEntity
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaEmptyState
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaPanel
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip

/**
 * AUDIT EVENT DETAIL.
 *
 * Plain language first, then the raw recorded line, then the chain fields. The entry is
 * re-read from the encrypted store by id rather than passed through navigation, so what is
 * displayed is what is actually stored.
 */
@Composable
fun AuditEventDetailScreen(
    entryId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var entry by remember(entryId) { mutableStateOf<AuditEntity?>(null) }
    var loaded by remember(entryId) { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        entry = runCatching {
            DatabaseProvider.get(context).auditDao().byId(entryId)
        }.getOrNull()
        loaded = true
    }

    val current = entry
    ThrakshaScaffold(title = "Event detail", onBack = onBack) { padding ->
        if (current == null) {
            if (loaded) {
                ThrakshaEmptyState(
                    title = "Entry not found",
                    message = "This audit entry could not be read from the encrypted store.",
                    icon = Icons.Default.SearchOff,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@ThrakshaScaffold
        }

        val presentation = AuditPresentation.of(current)
        ThrakshaPageColumn(padding = padding) {
            ThrakshaCard(ribbon = presentation.tone) {
                ThrakshaStatusChip(presentation.category.label, presentation.tone)
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = fullTimestamp(current.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = presentation.detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            ThrakshaCard {
                ThrakshaDisclosure("Technical details") {
                    ThrakshaFactRow("Entry", "#${current.id}")
                    ThrakshaFactRow("Type", current.type)
                    ThrakshaFactRow("Confidence tier", current.tier.toString())
                    ThrakshaFactRow("Recorded at", current.timestamp.toString())
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Text(
                        text = "Recorded line",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ThrakshaPanel {
                        Text(
                            text = current.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Text(
                        text = "Chain",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ThrakshaPanel {
                        ThrakshaFactRow("This entry", current.hash.abbreviate())
                        ThrakshaFactRow("Previous entry", current.prevHash.abbreviate())
                    }
                }
            }

            ThrakshaFootnote(
                "Every entry carries a fingerprint of the entry before it. If a record " +
                    "were removed or edited, the chain would no longer line up.",
            )
        }
    }
}

private fun String.abbreviate(): String =
    if (length <= 24) this else "${take(12)}…${takeLast(12)}"
