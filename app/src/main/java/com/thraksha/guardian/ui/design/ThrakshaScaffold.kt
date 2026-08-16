package com.thraksha.guardian.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thraksha.guardian.ui.components.TRLogo
import com.thraksha.guardian.ui.components.TRLogoSize

/**
 * The app shell.
 *
 * Guardian Prime suppresses the bottom navigation on focused, transactional screens (a
 * detail view, a plan preview) and keeps it on the three top-level destinations. That rule
 * is expressed here by simply not passing [bottomBar] on those screens, which also means
 * the content padding is correct in both cases — the nav bar never overlays content.
 */

/** A top-level destination. */
enum class ThrakshaDestination(
    val label: String,
    val icon: ImageVector,
) {
    PROTECT("Protect", Icons.Filled.Shield),
    AUTOMATE("Automate", Icons.Filled.Bolt),
    AUDIT("Audit", Icons.Filled.Analytics),
}

/**
 * Screen container. Applies the system-bar insets, the standard lateral margin and the
 * vertical rhythm, and gives scrolling content enough bottom clearance for the nav bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThrakshaScaffold(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBrand: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showBrand) {
                            TRLogo(size = TRLogoSize.SM)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(ThrakshaSpacing.sm))
                        }
                        if (title != null) {
                            Text(
                                text = title,
                                style = if (showBrand) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.headlineSmall
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/**
 * Bottom navigation: three destinations, pill highlight behind the active icon.
 *
 * Implemented directly rather than with `NavigationBar` so the Guardian Prime pill
 * geometry and tonal bar are exact, while keeping `selectable` semantics for TalkBack.
 */
@Composable
fun ThrakshaBottomNavigation(
    current: ThrakshaDestination,
    onSelect: (ThrakshaDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ThrakshaDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = ThrakshaSpacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThrakshaDestination.entries.forEach { destination ->
                ThrakshaNavItem(
                    destination = destination,
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

@Composable
private fun ThrakshaNavItem(
    destination: ThrakshaDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val indicator by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        label = "nav-indicator",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = ThrakshaSpacing.xs, horizontal = ThrakshaSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(ThrakshaRadius.pill))
                .background(indicator)
                .padding(horizontal = ThrakshaSpacing.xl, vertical = ThrakshaSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

/**
 * Standard vertical page body: lateral margin, section rhythm, and bottom clearance so
 * the last card is never trapped under the navigation bar.
 */
@Composable
fun ThrakshaPageColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(
                start = ThrakshaSpacing.screenMargin,
                end = ThrakshaSpacing.screenMargin,
                top = ThrakshaSpacing.sm,
                bottom = ThrakshaSpacing.xxl,
            ),
        verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sectionGap),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
