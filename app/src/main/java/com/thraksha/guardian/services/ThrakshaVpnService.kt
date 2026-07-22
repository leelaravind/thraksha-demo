package com.thraksha.guardian.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thraksha.guardian.MainActivity
import com.thraksha.guardian.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local VPN loopback for network traffic monitoring (Security Guardian / Day 6).
 * Establishes a TUN interface and runs a coroutine-based pass-through packet loop.
 */
class ThrakshaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetJob: Job? = null
    private val isVpnActive = AtomicBoolean(false)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpn()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "VPN service destroying")
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpn() {
        if (isVpnActive.get()) {
            Log.d(TAG, "VPN already active, ignoring start request")
            return
        }

        try {
            createNotificationChannel()
            startForegroundService()
            establishVpnInterface()
            startPacketProcessing()
            isVpnActive.set(true)
            Log.i(TAG, "VPN started: $VPN_ADDRESS/$VPN_PREFIX_LENGTH, MTU=$VPN_MTU")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error starting VPN: ${e.message}", e)
            stopVpn()
        }
    }

    private fun establishVpnInterface() {
        closeVpnInterface()
        val interfaceFd = Builder()
            .setSession(SESSION_NAME)
            .setMtu(VPN_MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
            .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX)
            .establish()

        if (interfaceFd == null) {
            throw IllegalStateException("VpnService.Builder.establish() returned null")
        }

        vpnInterface = interfaceFd
        Log.i(TAG, "VPN interface established (session=$SESSION_NAME)")
    }

    private fun startPacketProcessing() {
        packetJob?.cancel()
        val fd = vpnInterface ?: throw IllegalStateException("VPN interface not established")
        packetJob = serviceScope.launch {
            Log.i(TAG, "Packet processing started")
            try {
                processPackets(fd)
            } catch (e: IOException) {
                Log.e(TAG, "Packet processing I/O error: ${e.message}", e)
                if (isActive) {
                    handleFatalError("Packet I/O failure")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Packet processing error: ${e.message}", e)
                if (isActive) {
                    handleFatalError("Packet processing failure")
                }
            } finally {
                Log.i(TAG, "Packet processing stopped")
            }
        }
    }

    private suspend fun processPackets(interfaceFd: ParcelFileDescriptor) {
        val buffer = ByteArray(VPN_MTU)
        FileInputStream(interfaceFd.fileDescriptor).use { input ->
            FileOutputStream(interfaceFd.fileDescriptor).use { output ->
                while (serviceScope.isActive && packetJob?.isActive == true) {
                    val length = try {
                        input.read(buffer)
                    } catch (e: IOException) {
                        if (isVpnActive.get()) {
                            throw e
                        }
                        break
                    }

                    when {
                        length > 0 -> {
                            try {
                                output.write(buffer, 0, length)
                            } catch (e: IOException) {
                                Log.e(TAG, "Failed to write packet ($length bytes): ${e.message}", e)
                                throw e
                            }
                        }
                        length == 0 -> yield()
                        else -> break
                    }
                }
            }
        }
    }

    private fun handleFatalError(reason: String) {
        Log.e(TAG, "Stopping VPN due to fatal error: $reason")
        stopVpn()
    }

    private fun stopVpn() {
        packetJob?.cancel()
        packetJob = null
        closeVpnInterface()
        isVpnActive.set(false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed: ${e.message}", e)
        }

        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private fun closeVpnInterface() {
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}", e)
        } finally {
            vpnInterface = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.vpn_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        val contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "ThrakshaVPN"

        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX = 0
        private const val SESSION_NAME = "Thraksha VPN Monitor"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "thraksha_vpn"

        const val ACTION_STOP = "com.thraksha.guardian.vpn.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ThrakshaVpnService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ThrakshaVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Returns the system VPN consent intent, or null if already approved.
         */
        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}
