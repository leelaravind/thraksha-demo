package com.thraksha.guardian

import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.events.Severity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityEventBusTest {

    @Test
    fun emittedEvent_isReceivedByCollector() = runTest {
        val expected = SecurityEvent.ThreatDetected(
            signalId = "high-risk-install",
            severity = Severity.HIGH,
            details = "test",
            confidence = 90,
        )
        // UNDISPATCHED: the collector subscribes synchronously (suspends at first())
        // before we emit, so there is no subscribe/emit race and no replay dependency.
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            SecurityEventBus.events.first()
        }
        SecurityEventBus.emit(expected)
        assertEquals(expected, received.await())
    }

    @Test
    fun eventHierarchy_carriesFields() {
        val action = SecurityEvent.ActionTaken("lock", "device", "ok")
        val mode = SecurityEvent.ModeChanged("DEVICE_OWNER")
        assertEquals("lock", action.action)
        assertEquals("DEVICE_OWNER", mode.level)
    }
}
