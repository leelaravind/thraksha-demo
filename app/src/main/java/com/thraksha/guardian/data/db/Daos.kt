package com.thraksha.guardian.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLogDao {
    @Insert
    suspend fun insert(entry: AppLogEntity): Long

    @Query("SELECT * FROM app_log ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AppLogEntity>

    @Query("SELECT COUNT(*) FROM app_log")
    suspend fun count(): Int
}

@Dao
interface ConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: ConfigEntity)

    @Query("SELECT * FROM config WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): ConfigEntity?

    @Query("SELECT * FROM config")
    suspend fun all(): List<ConfigEntity>
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntity)

    @Query("SELECT * FROM audit_log ORDER BY id ASC")
    suspend fun allOrdered(): List<AuditEntity>

    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun last(): AuditEntity?

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun count(): Long

    /** Live feed for the dashboard (newest first). */
    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntity>>

    /** Single entry lookup for the audit detail view. Read-only; the chain is untouched. */
    @Query("SELECT * FROM audit_log WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AuditEntity?
}
