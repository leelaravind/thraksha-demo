package com.thraksha.guardian.ui.design

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.thraksha.guardian.data.config.ConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's appearance choice. Persisted in the encrypted config table so it survives
 * reinstall-free restarts like every other Thraksha setting.
 */
enum class ThemePreference(val label: String) {
    SYSTEM("Match system"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    companion object {
        private const val KEY = "ui.theme_preference"

        private val _current = MutableStateFlow(SYSTEM)

        /** Observed by the theme; never written to directly by screens. */
        val current: StateFlow<ThemePreference> = _current.asStateFlow()

        /** Reads the stored preference. Safe to call before the store is ready. */
        suspend fun load(context: Context) {
            val stored = runCatching { ConfigStore(context).getRawValue(KEY) }.getOrNull()
            _current.value = stored?.let { name ->
                runCatching { valueOf(name) }.getOrNull()
            } ?: SYSTEM
        }

        /** Applies immediately and persists. */
        suspend fun set(context: Context, preference: ThemePreference) {
            _current.value = preference
            runCatching { ConfigStore(context).putRawValue(KEY, preference.name) }
        }
    }
}

/**
 * True when the app is currently rendering its dark palette. Screens that need to pick a
 * theme-dependent asset read this rather than calling [isSystemInDarkTheme] themselves,
 * which would ignore an explicit Light/Dark preference.
 */
val LocalIsDarkTheme = compositionLocalOf { true }

/**
 * The Thraksha theme. One implementation, two token sets — no screen is duplicated per
 * theme, and no layout branches on [darkTheme].
 *
 * Edge-to-edge is enabled here and every screen consumes the resulting insets through
 * `ThrakshaScaffold`, so status- and navigation-bar space is real padding rather than a
 * guessed constant.
 */
@Composable
fun ThrakshaTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ThrakshaDarkColorScheme else ThrakshaLightColorScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Let the app's own surface show through the bars; without this the system
            // paints its default scrim and the top of the app reads as a grey band.
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            // System bars draw over the app background; icon tint must invert with theme
            // or the clock disappears into the canvas.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalThrakshaStatusColors provides statusColors,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ThrakshaTypography,
            shapes = ThrakshaShapes,
            content = content,
        )
    }
}

@Composable
private fun resolveDarkTheme(): Boolean {
    val preference by ThemePreference.current.collectAsState()
    return when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
}

/** Shorthand for the semantic status palette. */
object ThrakshaTheme {
    val status: ThrakshaStatusColors
        @Composable get() = LocalThrakshaStatusColors.current
}
