package com.thraksha.guardian.security

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide readiness of the secure foundation (SQLCipher native lib, Keystore-wrapped
 * passphrase, encrypted Room store, bus → audit collector).
 *
 * The point of this type is honesty. If the encrypted store cannot be opened — a
 * Keystore key invalidated by a restore, a corrupted DB, a missing native library — the
 * app must say so rather than run with a silently disabled audit trail while the UI
 * still claims protection is active. Nothing here creates a replacement database or
 * weakens fail-closed behaviour; it only records and publishes the failure.
 */
object FoundationStatus {

    private const val TAG = "FoundationStatus"

    sealed interface State {
        /** Startup is in flight; nothing has failed yet. */
        data object Initialising : State

        /** Encrypted store opened and the audit collector is subscribed. */
        data object Ready : State

        /** A required part of the secure foundation could not be initialised. */
        data class Failed(val message: String, val cause: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Initialising)
    val state: StateFlow<State> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value is State.Ready

    fun markReady() {
        if (_state.value !is State.Failed) _state.value = State.Ready
    }

    /** Records a hard initialisation failure. Sticky — the first failure is the useful one. */
    fun reportFailure(message: String, cause: Throwable? = null) {
        Log.e(TAG, "Secure foundation initialisation failed: $message", cause)
        if (_state.value !is State.Failed) {
            _state.value = State.Failed(message, cause?.let { "${it::class.java.simpleName}: ${it.message}" })
        }
    }

    /** Test-only reset so instrumented tests can exercise both branches. */
    internal fun resetForTest() {
        _state.value = State.Initialising
    }
}
