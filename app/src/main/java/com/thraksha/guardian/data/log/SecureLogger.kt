package com.thraksha.guardian.data.log

import android.content.Context
import com.thraksha.guardian.data.db.AppLogEntity
import com.thraksha.guardian.data.db.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes sensitive log entries into the encrypted `app_log` table instead of plaintext
 * logcat. Fire-and-forget on an IO scope. Non-sensitive debug output may still use
 * android.util.Log.
 */
object SecureLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(context: Context, level: Level, tag: String, message: String) {
        val appContext = context.applicationContext
        scope.launch {
            DatabaseProvider.get(appContext).appLogDao().insert(
                AppLogEntity(
                    timestamp = System.currentTimeMillis(),
                    level = level.name,
                    tag = tag,
                    message = message,
                ),
            )
        }
    }

    fun d(context: Context, tag: String, message: String) = log(context, Level.DEBUG, tag, message)
    fun i(context: Context, tag: String, message: String) = log(context, Level.INFO, tag, message)
    fun w(context: Context, tag: String, message: String) = log(context, Level.WARN, tag, message)
    fun e(context: Context, tag: String, message: String) = log(context, Level.ERROR, tag, message)
}
