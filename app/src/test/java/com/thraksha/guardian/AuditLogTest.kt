package com.thraksha.guardian

import com.thraksha.guardian.data.audit.AuditHasher
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.db.AuditEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Off-device verification of the hash-chain integrity logic. */
class AuditLogTest {

    private fun buildChain(vararg rows: Triple<String, String, Int>): List<AuditEntity> {
        val out = mutableListOf<AuditEntity>()
        var prevHash = AuditHasher.GENESIS
        var id = 0L
        var ts = 1_000L
        for ((type, details, tier) in rows) {
            id += 1
            ts += 1
            val hash = AuditHasher.hash(id, ts, type, details, prevHash)
            out.add(AuditEntity(id, ts, type, details, tier, prevHash, hash))
            prevHash = hash
        }
        return out
    }

    @Test
    fun validChain_verifies() {
        val chain = buildChain(
            Triple("THREAT", "a", 80),
            Triple("ACTION", "b", 0),
            Triple("THREAT", "c", 95),
        )
        assertTrue(AuditLog.verify(chain))
    }

    @Test
    fun tamperedDetails_breaksChain() {
        val chain = buildChain(
            Triple("THREAT", "a", 80),
            Triple("ACTION", "b", 0),
            Triple("THREAT", "c", 95),
        ).toMutableList()
        // simulate a direct DB row edit: change details, leave the stored hash untouched
        chain[1] = chain[1].copy(details = "TAMPERED")
        assertFalse(AuditLog.verify(chain))
    }
}
