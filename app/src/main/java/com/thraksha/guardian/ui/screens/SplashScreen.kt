package com.thraksha.guardian.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.thraksha.guardian.ui.components.TRLogo
import com.thraksha.guardian.ui.components.TRLogoSize
import com.thraksha.guardian.ui.components.Wordmark
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onComplete: () -> Unit = {}
) {
    var logoVisible by remember { mutableStateOf(false) }
    var wordmarkVisible by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "logo_alpha"
    )

    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (wordmarkVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "wordmark_alpha"
    )

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(800)
        wordmarkVisible = true
        delay(2000)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Theme-aware: the brand mark reads on both palettes, but the canvas and
            // wordmark must follow the active theme or the light build is unreadable.
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TRLogo(
                size = TRLogoSize.LG,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )

            Wordmark(
                fontSize = 28,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(wordmarkAlpha)
            )
        }
    }
}