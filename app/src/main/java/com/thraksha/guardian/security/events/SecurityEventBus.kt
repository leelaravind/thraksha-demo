package com.thraksha.guardian.security.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide security event bus. Producers call [emit] / [tryEmit]; consumers collect
 * [events]. No replay (live stream), but a generous buffer so a momentarily slow
 * collector (e.g. the audit writer) does not drop emissions.
 */
object SecurityEventBus {

    private val _events = MutableSharedFlow<SecurityEvent>(
        replay = 0,
        extraBufferCapacity = 128,
    )

    val events: SharedFlow<SecurityEvent> = _events.asSharedFlow()

    suspend fun emit(event: SecurityEvent) = _events.emit(event)

    /** Non-suspending emit; returns false only if the buffer is momentarily full. */
    fun tryEmit(event: SecurityEvent): Boolean = _events.tryEmit(event)
}
