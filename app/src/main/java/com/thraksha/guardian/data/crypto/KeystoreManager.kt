package com.thraksha.guardian.data.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the SQLCipher database passphrase.
 *
 * The passphrase is 32 random bytes generated once, then wrapped (AES/GCM) by a
 * hardware-backed key held in the AndroidKeyStore (StrongBox when the device offers
 * it). Only the IV + ciphertext are persisted (in plain SharedPreferences — they are
 * useless without the Keystore key, which never leaves secure hardware). The raw
 * passphrase is never persisted and never hardcoded. No user-auth requirement, since
 * the DB is opened by a background service.
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "thraksha_db_master_key"
    private const val PREFS = "thraksha_secure_prefs"
    private const val PREF_IV = "db_pass_iv"
    private const val PREF_CT = "db_pass_ct"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    /**
     * Returns the DB passphrase, generating + wrapping it on first call and
     * unwrapping the stored ciphertext thereafter. Stable across process restarts.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ivB64 = prefs.getString(PREF_IV, null)
        val ctB64 = prefs.getString(PREF_CT, null)

        if (ivB64 != null && ctB64 != null) {
            return unwrap(
                iv = Base64.decode(ivB64, Base64.NO_WRAP),
                ciphertext = Base64.decode(ctB64, Base64.NO_WRAP),
            )
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val (iv, ciphertext) = wrap(passphrase)
        prefs.edit()
            .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(PREF_CT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun wrap(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv to ciphertext
    }

    private fun unwrap(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        // StrongBox is API 28+ and absent on many devices — try it, fall back to TEE.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGen.init(builder.setIsStrongBoxBacked(true).build())
                return keyGen.generateKey()
            } catch (e: Exception) {
                keyGen.init(builder.setIsStrongBoxBacked(false).build())
                return keyGen.generateKey()
            }
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }
}
