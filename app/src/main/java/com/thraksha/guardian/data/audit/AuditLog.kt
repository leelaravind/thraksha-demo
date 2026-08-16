package com.thraksha.guardian.data.audit

import com.thraksha.guardian.data.db.AuditDao
import com.thraksha.guardian.data.db.AuditEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Append-only, hash-chained audit log over the encrypted `audit_log` table.
 *
 * Exposes only [append] and [verifyChain] — no update or delete API. Each entry chains
 * to its predecessor's hash (the first from a fixed genesis), so altering any recorded
 * row is detectable by [verifyChain].
 *
 * [append] performs read-last → derive-id → insert, which is only correct if it is not
 * interleaved. From Phase 1 onward there is more than one producer (the bus collector
 * and the security audit engine), so the sequence is serialised by a process-wide
 * [appendLock]. The lock is on the companion, not the instance, because more than one
 * [AuditLog] may wrap the same singleton DAO.
 */
class AuditLog(private val dao: AuditDao) {

    suspend fun append(
        type: String,
        details: String,
        tier: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): AuditEntity = appendLock.withLock {
        val previous = dao.last()
        val id = (previous?.id ?: 0L) + 1L
        val prevHash = previous?.hash ?: AuditHasher.GENESIS
        val hash = AuditHasher.hash(id, timestamp, type, details, prevHash)
        val entry = AuditEntity(id, timestamp, type, details, tier, prevHash, hash)
        dao.insert(entry)
        entry
    }

    suspend fun verifyChain(): Boolean = verify(dao.allOrdered())

    companion object {
        /** Serialises id assignment across every [AuditLog] instance in the process. */
        private val appendLock = Mutex()

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
