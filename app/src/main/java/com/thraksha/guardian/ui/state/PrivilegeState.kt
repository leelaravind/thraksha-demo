package com.thraksha.guardian.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability

/**
 * Privilege level as reactive Compose state, re-read on every ON_RESUME.
 *
 * The dashboard previously took a one-shot `produceState` snapshot, so enrolling Device
 * Admin / Device Owner from system Settings and returning to the app left it reporting
 * NORMAL until the Activity was recreated. Granting privilege from Settings is exactly
 * what happens during a demo, so the read has to follow the lifecycle.
 */
@Composable
fun rememberPrivilegeLevel(): State<PrivilegeLevel> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = remember(context) { context.applicationContext }

    val state = remember {
        mutableStateOf(runCatching { SecurityCapability.currentLevel(appContext) }
            .getOrDefault(PrivilegeLevel.NORMAL))
    }
    val currentState by rememberUpdatedState(state)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentState.value = runCatching { SecurityCapability.currentLevel(appContext) }
                    .getOrDefault(PrivilegeLevel.NORMAL)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}
