package com.thraksha.guardian

import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.events.Severity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityEventBusTest {

    @Test
    fun emittedEvent_isReceivedByCollector() = runTest {
        val expected = SecurityEvent.ThreatDetected(
            signalId = "high-risk-install",
            severity = Severity.HIGH,
            details = "test",
            confidence = 90,
        )
        val received = CompletableDeferred<SecurityEvent>()

        val job = backgroundScope.launch {
            SecurityEventBus.events.collect { received.complete(it) }
        }
        advanceUntilIdle()          // let the collector subscribe

        SecurityEventBus.emit(expected)
        advanceUntilIdle()          // let the collector process

        assertEquals(expected, received.await())
        job.cancel()
    }

    @Test
    fun eventHierarchy_carriesFields() {
        val action = SecurityEvent.ActionTaken("lock", "device", "ok")
        val mode = SecurityEvent.ModeChanged("DEVICE_OWNER")
        assertEquals("lock", action.action)
        assertEquals("DEVICE_OWNER", mode.level)
    }
}
