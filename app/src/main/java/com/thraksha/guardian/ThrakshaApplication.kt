package com.thraksha.guardian

import android.app.Application
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Loads the SQLCipher native library once per process (required before the encrypted
 * Room database is opened), seeds config defaults on first run, and subscribes the
 * audit log to the security event bus so ThreatDetected / ActionTaken events are
 * recorded in the tamper-evident chain.
 */
class ThrakshaApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // SQLCipher (net.zetetic:sqlcipher-android) requires the native lib before DB open.
        System.loadLibrary("sqlcipher")
        seedConfigDefaults()
        wireAuditLogToEventBus()
    }

    private fun seedConfigDefaults() {
        appScope.launch {
            runCatching { ConfigStore(this@ThrakshaApplication).ensureDefaults() }
        }
    }

    private fun wireAuditLogToEventBus() {
        val auditLog = AuditLog(DatabaseProvider.get(this).auditDao())
        appScope.launch {
            SecurityEventBus.events.collect { event ->
                runCatching {
                    when (event) {
                        is SecurityEvent.ThreatDetected -> auditLog.append(
                            type = "THREAT",
                            details = "${event.signalId} [${event.severity}] ${event.details}",
                            tier = event.confidence,
                        )
                        is SecurityEvent.ActionTaken -> auditLog.append(
                            type = "ACTION",
                            details = "${event.action} -> ${event.target}: ${event.result}",
                            tier = 0,
                        )
                        is SecurityEvent.ModeChanged -> Unit
                    }
                }
            }
        }
    }
}
