package com.thraksha.guardian.security.rulepack

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the RSA-2048 / SHA-256 signature over the raw rulepack bytes.
 *
 * Pure JVM (java.security + java.util.Base64, both available on API 26+) so it is
 * unit-testable off-device. Any change to the signed bytes makes [verify] return false.
 */
object RulepackVerifier {

    fun verify(jsonBytes: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean =
        runCatching {
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())),
            )
            Signature.getInstance("SHA256withRSA").run {
                initVerify(publicKey)
                update(jsonBytes)
                verify(Base64.getDecoder().decode(signatureBase64.trim()))
            }
        }.getOrDefault(false)
}
