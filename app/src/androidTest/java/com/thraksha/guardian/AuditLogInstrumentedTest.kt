package com.thraksha.guardian

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.crypto.KeystoreManager
import com.thraksha.guardian.data.db.ThrakshaDatabase
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device audit-chain verification over the real encrypted DB: a freshly appended
 * chain verifies, and a direct row edit (bypassing the append API) breaks it.
 */
@RunWith(AndroidJUnit4::class)
class AuditLogInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "audit_test.db"
    private lateinit var db: ThrakshaDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        val passphrase = KeystoreManager.getOrCreatePassphrase(context)
        db = Room.databaseBuilder(context, ThrakshaDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun appendedChainVerifies_thenDirectRowEditBreaksIt() = runBlocking {
        val audit = AuditLog(db.auditDao())
        audit.append("THREAT", "one", 80)
        audit.append("ACTION", "two", 0)
        audit.append("THREAT", "three", 95)

        assertEquals(3L, db.auditDao().count())
        assertTrue("freshly appended chain must verify", audit.verifyChain())

        // Corrupt a row directly in the encrypted DB, leaving its stored hash stale.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE audit_log SET details = 'HACKED' WHERE id = 2",
        )
        assertFalse("tampered chain must fail verification", audit.verifyChain())
    }
}
