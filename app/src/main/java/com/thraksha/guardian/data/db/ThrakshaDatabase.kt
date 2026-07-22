package com.thraksha.guardian.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The encrypted local store (SQLCipher-backed via [DatabaseProvider]).
 * Holds the security spine's tables: structured logs, config, and the audit chain.
 */
@Database(
    entities = [AppLogEntity::class, ConfigEntity::class, AuditEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ThrakshaDatabase : RoomDatabase() {
    abstract fun appLogDao(): AppLogDao
    abstract fun configDao(): ConfigDao
    abstract fun auditDao(): AuditDao
}
