package com.thraksha.guardian.data.db

import android.content.Context
import androidx.room.Room
import com.thraksha.guardian.data.crypto.KeystoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds and caches the single encrypted [ThrakshaDatabase] instance.
 *
 * The passphrase comes from [KeystoreManager] (Keystore-wrapped, never hardcoded) and
 * is handed to SQLCipher's [SupportOpenHelperFactory]. Requires the native lib loaded
 * once (done in ThrakshaApplication.onCreate via System.loadLibrary("sqlcipher")).
 */
object DatabaseProvider {

    private const val DB_NAME = "thraksha_secure.db"

    @Volatile
    private var instance: ThrakshaDatabase? = null

    fun get(context: Context): ThrakshaDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ThrakshaDatabase {
        val passphrase = KeystoreManager.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, ThrakshaDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .build()
    }
}
