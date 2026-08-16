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
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.security.network.NetworkGuardEngine
import com.thraksha.guardian.security.network.VpnScope
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thraksha Network Guard (Phase 7). A **per-app-scoped** VpnService: only
 * `com.thraksha.demo.villaincaller` enters the tunnel via `addAllowedApplication`, and
 * only the RFC 5737 TEST-NET-3 demo route is claimed. There is no device-wide capture
 * path — if scoping fails the service fails closed to [NetworkGuard.State.Error] and
 * establishes nothing (guide §1, §7, §28).
 *
 * The service owns only: TUN lifecycle, the packet read loop, protected outbound
 * forwarding / intentional drop, and the foreground notification. All threat-intel and
 * policy logic lives in [NetworkGuardEngine]; this class renders no verdicts itself
 * (guide §17).
 *
 * The old echo loop that black-holed device traffic is gone entirely.
 */
class ThrakshaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetJob: Job? = null
    private val isActiveFlag = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var engine: NetworkGuardEngine

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGuard(NetworkGuard.State.Disabled)
                return START_NOT_STICKY
            }
            else -> {
                startGuard()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopGuard(NetworkGuard.State.Disabled)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startGuard() {
        if (isActiveFlag.get()) return
        NetworkGuard.transition(NetworkGuard.State.Starting)
        try {
            engine = NetworkGuardEngine(this)
            createNotificationChannel()
            startForegroundService()
            establishScopedTunnel()
            startPacketLoop()
            isActiveFlag.set(true)
            NetworkGuard.transition(
                NetworkGuard.State.Active(
                    scopedPackage = VpnScope.ALLOWED_PACKAGES.single(),
                    tunnelAddress = "$VPN_ADDRESS/$VPN_PREFIX",
                ),
            )
            Log.i(TAG, "Network Guard active, scoped to ${VpnScope.ALLOWED_PACKAGES}")
        } catch (error: Exception) {
            Log.e(TAG, "Network Guard failed to start: ${error.message}", error)
            stopGuard(NetworkGuard.State.Error(error.message ?: "tunnel start failed"))
        }
    }

    /**
     * Builds the tunnel scoped to exactly the allowed package. If
     * `addAllowedApplication` throws (package absent / not installable), the whole start
     * fails — there is no fallback to a broad tunnel (the §28 invariant).
     */
    private fun establishScopedTunnel() {
        closeInterface()
        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(VPN_MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX)
            .addRoute(VpnScope.ROUTE_ADDRESS, VpnScope.ROUTE_PREFIX)

        var scoped = 0
        for (pkg in VpnScope.ALLOWED_PACKAGES) {
            builder.addAllowedApplication(pkg) // throws if the package cannot be scoped
            scoped++
        }
        check(scoped == VpnScope.ALLOWED_PACKAGES.size && scoped > 0) {
            "per-app scoping incomplete ($scoped/${VpnScope.ALLOWED_PACKAGES.size}) — refusing broad tunnel"
        }

        vpnInterface = builder.establish()
            ?: throw IllegalStateException("VpnService.Builder.establish() returned null")
        Log.i(TAG, "Scoped TUN established (session=$SESSION_NAME)")
    }

    private fun startPacketLoop() {
        packetJob?.cancel()
        val fd = vpnInterface ?: throw IllegalStateException("TUN not established")
        packetJob = serviceScope.launch {
            val buffer = ByteArray(VPN_MTU)
            FileInputStream(fd.fileDescriptor).use { input ->
                FileOutputStream(fd.fileDescriptor).use { _ ->
                    while (isActive && isActiveFlag.get()) {
                        val length = try {
                            input.read(buffer)
                        } catch (io: Exception) {
                            if (isActiveFlag.get()) Log.w(TAG, "read error: ${io.message}")
                            break
                        }
                        if (length <= 0) {
                            yield()
                            continue
                        }
                        // A malformed/unsupported packet must never break the loop.
                        runCatching { handlePacket(buffer, length) }
                            .onFailure { Log.e(TAG, "packet handling error", it) }
                    }
                }
            }
        }
    }

    private suspend fun handlePacket(buffer: ByteArray, length: Int) {
        when (val verdict = engine.evaluate(buffer, length)) {
            is NetworkGuardEngine.Verdict.Ignore -> NetworkGuard.recordIgnored()

            is NetworkGuardEngine.Verdict.Block -> {
                // Do NOT create the forwarding socket. Dropping is the whole action.
                engine.publishBlockOutcome(verdict)
                Log.i(TAG, "BLOCKED ${verdict.observation.endpoint()} (dropped before forward)")
            }

            is NetworkGuardEngine.Verdict.UserBlock -> {
                // User-ordered block (Phase 8.1 ACT): same no-forwarding-socket path.
                engine.publishUserBlockOutcome(verdict)
                Log.i(TAG, "USER-BLOCKED ${verdict.observation.endpoint()} (dropped before forward)")
            }

            is NetworkGuardEngine.Verdict.Forward -> {
                val ok = forwardUdp(verdict.observation, buffer, verdict.payload)
                engine.publishForwardOutcome(verdict, sendSucceeded = ok)
            }
        }
    }

    /**
     * Controlled outbound-only UDP forwarder (guide §10): a fresh DatagramSocket,
     * `protect()`ed so it does not loop back into the tunnel, sends the payload to the
     * original destination, then closes. No response path — the demo never waits for a
     * reply. Not a general UDP VPN.
     */
    private fun forwardUdp(
        observation: com.thraksha.guardian.security.network.NetworkObservation,
        buffer: ByteArray,
        payload: NetworkPacketWindow,
    ): Boolean = try {
        DatagramSocket().use { socket ->
            check(protect(socket)) { "VpnService.protect(socket) failed" }
            val data = buffer.copyOfRange(payload.offset, payload.offset + payload.length)
            socket.send(
                DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(observation.destinationIp),
                    observation.destinationPort,
                ),
            )
        }
        true
    } catch (error: Exception) {
        Log.w(TAG, "UDP forward failed: ${error.message}")
        false
    }

    private fun stopGuard(finalState: NetworkGuard.State) {
        packetJob?.cancel()
        packetJob = null
        closeInterface()
        isActiveFlag.set(false)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
        }
        NetworkGuard.transition(finalState)
        stopSelf()
        Log.i(TAG, "Network Guard stopped ($finalState)")
    }

    private fun closeInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Thraksha Network Guard",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Monitors the scoped demo app's outbound traffic." },
        )
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Thraksha Network Guard")
            .setContentText("Monitoring VillainCaller network traffic")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "ThrakshaNetworkGuard"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.113.0.2"
        private const val VPN_PREFIX = 32
        private const val SESSION_NAME = "Thraksha Network Guard"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "thraksha_network_guard"

        const val ACTION_STOP = "com.thraksha.guardian.networkguard.STOP"

        /** The system VPN-consent intent, or null if consent is already held. */
        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ThrakshaVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ThrakshaVpnService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}

/** Local alias so the service signature reads cleanly. */
private typealias NetworkPacketWindow =
    com.thraksha.guardian.security.network.NetworkPacketParser.PayloadWindow
