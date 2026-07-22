package com.thraksha.guardian

import com.thraksha.guardian.security.rulepack.Rulepack
import com.thraksha.guardian.security.rulepack.RulepackVerifier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Off-device verification of the bundled, signed rulepack: the real asset bytes verify
 * and parse to all 12 rules, and any single-byte tamper is rejected.
 */
class RulepackVerifierTest {

    private fun asset(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.exists() }
            ?: error("asset not found: $name (wd=${File(".").absolutePath})")

    private val jsonBytes = asset("rulepack.json").readBytes()
    private val signature = asset("rulepack.sig").readText()
    private val publicKey = asset("rulepack_public.key").readText()

    @Test
    fun validRulepack_verifiesAndParsesTwelveEnabledRules() {
        assertTrue("signature must verify", RulepackVerifier.verify(jsonBytes, signature, publicKey))

        val rulepack = Json { ignoreUnknownKeys = true }
            .decodeFromString(Rulepack.serializer(), jsonBytes.decodeToString())
        assertEquals(1, rulepack.version)
        assertEquals(12, rulepack.rules.size)
        assertTrue("all v1 rules enabled", rulepack.rules.all { it.enabled })
        assertEquals(12, rulepack.rules.map { it.id }.toSet().size) // ids unique
    }

    @Test
    fun tamperedRulepack_failsVerification() {
        val tampered = jsonBytes.copyOf()
        val i = tampered.size / 2
        tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
        assertFalse("flipped byte must break the signature", RulepackVerifier.verify(tampered, signature, publicKey))
    }
}
