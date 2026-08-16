package com.thraksha.guardian.security.threatintel

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * The supported identifiers of one installed app, as observable through documented
 * Android APIs. What is (and is not) fingerprinted:
 *
 *  * [certSha256] — SHA-256 of each current signing certificate, via
 *    `GET_SIGNING_CERTIFICATES` / `SigningInfo` (the supported replacement for the
 *    legacy `GET_SIGNATURES` API). Rotation history is included when present.
 *  * [baseApkSha256] — SHA-256 of the installed **base APK only**
 *    (`ApplicationInfo.sourceDir`), streamed, never loaded whole into memory. Split
 *    APKs are not hashed; the indicator name `BASE_APK_SHA256` says so.
 */
data class AppFingerprint(
    val packageName: String,
    val certSha256: Set<String>,
    val baseApkSha256: String?,
    /** Milliseconds spent on certificate digest / APK hash — for §6.18 measurement. */
    val certDurationMs: Long,
    val apkHashDurationMs: Long,
)

/** Computes [AppFingerprint]s. The only threat-intel class that touches Android APIs. */
class AppFingerprinter(private val packageManager: PackageManager) {

    /** Fingerprints one visible package, or null when it cannot be resolved at all. */
    fun fingerprint(packageName: String): AppFingerprint? {
        val certStart = System.nanoTime()
        val certs = runCatching { signingCertSha256s(packageName) }.getOrElse { error ->
            Log.w(TAG, "Signing info unavailable for $packageName: ${error.message}")
            return null
        }
        val certMs = (System.nanoTime() - certStart) / 1_000_000

        val apkStart = System.nanoTime()
        val apkSha = runCatching { baseApkSha256(packageName) }.getOrElse { error ->
            // A missing/unreadable APK file downgrades the fingerprint, never crashes it.
            Log.w(TAG, "Base APK unreadable for $packageName: ${error.message}")
            null
        }
        val apkMs = (System.nanoTime() - apkStart) / 1_000_000

        return AppFingerprint(
            packageName = packageName,
            certSha256 = certs,
            baseApkSha256 = apkSha,
            certDurationMs = certMs,
            apkHashDurationMs = apkMs,
        )
    }

    private fun signingCertSha256s(packageName: String): Set<String> {
        val info = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures: Array<Signature> = when {
            signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
            // Single signer: history covers the current cert plus any rotated-from certs.
            else -> signingInfo.signingCertificateHistory
        } ?: return emptySet()
        return signatures.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun baseApkSha256(packageName: String): String? {
        val sourceDir = packageManager.getApplicationInfo(packageName, 0).sourceDir
            ?: return null
        val file = File(sourceDir)
        if (!file.canRead()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "AppFingerprinter"
    }
}
