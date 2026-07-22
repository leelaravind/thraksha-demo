package com.thraksha.guardian.data.audit

import com.thraksha.guardian.data.db.AuditDao
import com.thraksha.guardian.data.db.AuditEntity

/**
 * Append-only, hash-chained audit log over the encrypted `audit_log` table.
 *
 * Exposes only [append] and [verifyChain] — no update or delete API. Each entry chains
 * to its predecessor's hash (the first from a fixed genesis), so altering any recorded
 * row is detectable by [verifyChain].
 */
class AuditLog(private val dao: AuditDao) {

    suspend fun append(
        type: String,
        details: String,
        tier: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): AuditEntity {
        val previous = dao.last()
        val id = (previous?.id ?: 0L) + 1L
        val prevHash = previous?.hash ?: AuditHasher.GENESIS
        val hash = AuditHasher.hash(id, timestamp, type, details, prevHash)
        val entry = AuditEntity(id, timestamp, type, details, tier, prevHash, hash)
        dao.insert(entry)
        return entry
    }

    suspend fun verifyChain(): Boolean = verify(dao.allOrdered())

    companion object {
        /** Pure verification over an id-ordered entry list — unit-testable off-device. */
        fun verify(entries: List<AuditEntity>): Boolean {
            var expectedPrev = AuditHasher.GENESIS
            for (entry in entries) {
                if (entry.prevHash != expectedPrev) return false
                val recomputed = AuditHasher.hash(
                    entry.id, entry.timestamp, entry.type, entry.details, entry.prevHash,
                )
                if (recomputed != entry.hash) return false
                expectedPrev = entry.hash
            }
            return true
        }
    }
}
