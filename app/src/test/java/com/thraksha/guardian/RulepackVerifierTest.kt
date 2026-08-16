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
 * and parse, and any single-byte tamper is rejected.
 *
 * The pack is v2 (demo-aligned). It carries 5 enabled rules that have real detectors in
 * RuleEngine, plus the original generic catalogue retained as `enabled: false` — those
 * have no detector, so leaving them enabled would imply evaluation that never happens.
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
    fun validRulepack_verifiesAndParses() {
        assertTrue("signature must verify", RulepackVerifier.verify(jsonBytes, signature, publicKey))

        val rulepack = Json { ignoreUnknownKeys = true }
            .decodeFromString(Rulepack.serializer(), jsonBytes.decodeToString())
        assertEquals(2, rulepack.version)
        assertEquals(17, rulepack.rules.size)
        assertEquals("ids unique", 17, rulepack.rules.map { it.id }.toSet().size)

        val enabled = rulepack.rules.filter { it.enabled }
        assertEquals("5 demo-aligned rules are active", 5, enabled.size)
        assertTrue(
            "every enabled rule must declare a check its detector implements",
            enabled.all { it.params.containsKey("check") },
        )
        assertTrue(
            "disabled catalogue rules must carry no detector params",
            rulepack.rules.filterNot { it.enabled }.all { it.params.isEmpty() },
        )
    }

    @Test
    fun tamperedRulepack_failsVerification() {
        val tampered = jsonBytes.copyOf()
        val i = tampered.size / 2
        tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
        assertFalse("flipped byte must break the signature", RulepackVerifier.verify(tampered, signature, publicKey))
    }
}
