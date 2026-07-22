package com.thraksha.guardian

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.crypto.KeystoreManager
import com.thraksha.guardian.data.db.AuditEntity
import com.thraksha.guardian.data.db.ConfigEntity
import com.thraksha.guardian.data.db.ThrakshaDatabase
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device (SQLCipher + Keystore) verification: passphrase stability, an encrypted
 * write then close then reopen then read round-trip, and proof the DB file is not
 * plaintext SQLite.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "roundtrip_test.db"

    private fun buildDb(passphrase: ByteArray): ThrakshaDatabase =
        Room.databaseBuilder(context, ThrakshaDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()

    @Test
    fun passphrase_isStableAndCorrectLength() {
        val a = KeystoreManager.getOrCreatePassphrase(context)
        val b = KeystoreManager.getOrCreatePassphrase(context)
        assertEquals(32, a.size)
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun roundTrip_writeCloseReopenRead_andCiphertextOnDisk() = runBlocking {
        context.deleteDatabase(dbName)
        val passphrase = KeystoreManager.getOrCreatePassphrase(context)

        // write, then fully close
        val db1 = buildDb(passphrase.copyOf())
        db1.configDao().put(ConfigEntity("mode", "OBSERVE"))
        db1.auditDao().insert(
            AuditEntity(1, 123L, "THREAT", "roundtrip", 80, "GENESIS", "hash1"),
        )
        db1.close()

        // reopen a fresh instance against the same file + passphrase, read back
        val db2 = buildDb(passphrase.copyOf())
        assertEquals("OBSERVE", db2.configDao().get("mode")?.value)
        assertEquals(1, db2.auditDao().count().toInt())
        db2.close()

        // the on-disk file must NOT carry the plaintext SQLite magic header
        val dbFile: File = context.getDatabasePath(dbName)
        assertTrue("db file should exist", dbFile.exists())
        val header = ByteArray(16)
        dbFile.inputStream().use { it.read(header) }
        val headerAscii = String(header, Charsets.US_ASCII)
        assertFalse(
            "DB must be encrypted (found plaintext SQLite header)",
            headerAscii.startsWith("SQLite format 3"),
        )
    }
}
