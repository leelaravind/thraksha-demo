package com.thraksha.guardian

import android.app.Application

/**
 * Application entry point.
 *
 * Loads the SQLCipher native library once per process (required before the encrypted
 * Room database is opened) and, from Phase 2 onward, wires the app-scoped foundation
 * (event bus → audit log). Kept deliberately thin — components own their own lifecycles.
 */
class ThrakshaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // SQLCipher (net.zetetic:sqlcipher-android) requires the native lib loaded before DB open.
        System.loadLibrary("sqlcipher")
    }
}
