package com.thraksha.guardian.data.audit

import java.security.MessageDigest

/**
 * Hashing for the tamper-evident audit chain.
 * hash = SHA-256(id | timestamp | type | details | prevHash), lowercase hex.
 */
object AuditHasher {

    /** prevHash of the very first entry. */
    const val GENESIS = "GENESIS"

    fun hash(id: Long, timestamp: Long, type: String, details: String, prevHash: String): String {
        val payload = "$id|$timestamp|$type|$details|$prevHash"
        val bytes = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
