package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.engine.AuditResult
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the last leg of the pipeline: findings emitted by [SecurityAuditEngine] are
 * written into the **encrypted, hash-chained** audit trail, and the chain still verifies
 * afterwards.
 *
 * The test deliberately installs **no collector of its own** — it relies on the one
 * `ThrakshaApplication` wires at process start, so what is exercised is the real
 * production path, and it asserts against the real SQLCipher-backed database.
 */
@RunWith(AndroidJUnit4::class)
class AuditTrailPersistenceInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun decoysInstalled(): Boolean =
        AppInventory(context.packageManager).inventory().size == DemoAppRegistry.demoPackages.size

    @Test
    fun auditFindingsArePersistedIntoTheHashChainedTrail() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        SecurityAuditEngine.resetForTest()

        val dao = DatabaseProvider.get(context).auditDao()

        // The app's own collector must already be subscribed; without it nothing would
        // be persisted at all (the bus has replay = 0).
        val subscribed = withTimeoutOrNull(10_000) {
            while (SecurityEventBus.subscriptionCount.value == 0) delay(20)
            true
        }
        assertTrue("ThrakshaApplication's audit collector must be subscribed", subscribed == true)

        // Let any in-flight rows (e.g. from the launch audit at process start) finish
        // draining, so `before` is a stable baseline rather than a mid-drain snapshot.
        withTimeoutOrNull(10_000) {
            var previous = -1L
            while (true) {
                val current = dao.count()
                if (current == previous) break
                previous = current
                delay(300)
            }
        }

        val before = dao.count()
        val result = SecurityAuditEngine.runAudit(context)
        assertTrue(result is AuditResult.Completed)
        val expectedNewRows = (result as AuditResult.Completed).threatsFound + 1 // + the SCAN row

        // Wait for the production collector to drain the emissions. The SCAN row is
        // always emitted last (after any Phase 4 DECISION/ADVISED rows), so require both
        // the minimum row count and the SCAN row before asserting.
        withTimeoutOrNull(15_000) {
            while (
                dao.count() < before + expectedNewRows ||
                dao.allOrdered().none { it.id > before && it.type == "SCAN" }
            ) {
                delay(50)
            }
        }

        assertTrue(
            "every finding plus the scan summary must be persisted " +
                "(before=$before, after=${dao.count()}, expected at least $expectedNewRows new)",
            dao.count() >= before + expectedNewRows,
        )

        val rows = dao.allOrdered()
        val newRows = rows.filter { it.id > before }

        assertTrue(
            "VillainCaller findings must be attributable in the trail",
            newRows.any {
                it.type == "THREAT" && it.details.contains(DemoAppRegistry.VILLAIN_CALLER)
            },
        )
        assertTrue(
            "GoodCaller must NOT appear as a threat",
            newRows.none {
                it.type == "THREAT" && it.details.contains(DemoAppRegistry.GOOD_CALLER)
            },
        )
        assertTrue(
            "a SCAN row must record that the audit ran at all",
            newRows.any { it.type == "SCAN" && it.details.contains("rulepack v2 verified") },
        )
        assertTrue(
            "the tamper-evident chain must still verify after the audit writes",
            AuditLog.verify(rows),
        )
    }
}
