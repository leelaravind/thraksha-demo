package com.thraksha.guardian.security.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.thraksha.guardian.MainActivity
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.scan.DeviceScanResult
import com.thraksha.guardian.security.scan.ScanClassification
import java.util.concurrent.atomic.AtomicReference

/**
 * The Phase 8.1 security-notification path (guide §20/§21). Deliberately quiet:
 *
 *  * declared-only (WATCHING) findings never notify;
 *  * REVIEW-level capability exposure produces at most ONE summary notification per
 *    scan, and only when the review set changed since the last one;
 *  * a KNOWN THREAT MATCH produces one high-priority notification per package per
 *    process;
 *  * wording states capability exposure / intelligence matches — never "virus detected"
 *    without a genuine strong match (§20), and never behavior that was not observed.
 *
 * Every notification deep-links to the dashboard with the package to focus, so the tap
 * lands on the evidence card (§20).
 */
object SecurityNotifier {

    private const val TAG = "SecurityNotifier"
    private const val CHANNEL_REVIEW = "thraksha_security_review"
    private const val CHANNEL_THREAT = "thraksha_threat_alert"
    private const val ID_REVIEW_SUMMARY = 3001
    private const val ID_THREAT_BASE = 3100

    const val EXTRA_FOCUS_PACKAGE = "com.thraksha.guardian.FOCUS_PACKAGE"

    /** Threat packages already notified this process — spam guard. */
    private val notifiedThreats = AtomicReference<Set<String>>(emptySet())

    /** The review set last notified — a summary repeats only when it changes. */
    private val lastReviewSet = AtomicReference<Set<String>>(emptySet())

    /** Called once per finished device scan. */
    fun notifyScanResult(context: Context, result: DeviceScanResult) {
        runCatching {
            ensureChannels(context)

            result.apps
                .filter { it.classification == ScanClassification.KNOWN_THREAT_MATCH }
                .forEach { record ->
                    notifyKnownThreat(context, record.packageName, record.displayName)
                }

            val reviewPackages = result.apps
                .filter {
                    it.displayStatus == AppDisplayStatus.REVIEW ||
                        it.displayStatus == AppDisplayStatus.ADVISED
                }
                .map { it.packageName }
                .toSet()

            if (reviewPackages.isNotEmpty() &&
                lastReviewSet.getAndSet(reviewPackages) != reviewPackages
            ) {
                val count = reviewPackages.size
                post(
                    context, ID_REVIEW_SUMMARY, CHANNEL_REVIEW,
                    title = "Thraksha Security Review",
                    text = if (count == 1) {
                        "1 app has broad capabilities enabled. Tap to review the evidence."
                    } else {
                        "$count apps have broad capabilities enabled. Tap to review the evidence."
                    },
                    focusPackage = reviewPackages.firstOrNull().takeIf { count == 1 },
                    highPriority = false,
                )
            }
        }.onFailure { Log.w(TAG, "Review notification failed", it) }
    }

    /** One high-priority notification per known-threat package per process. */
    fun notifyKnownThreat(context: Context, packageName: String, appName: String) {
        runCatching {
            ensureChannels(context)
            val already = notifiedThreats.getAndUpdate { it + packageName }
            if (packageName in already) return

            post(
                context,
                ID_THREAT_BASE + (packageName.hashCode() and 0xFF),
                CHANNEL_THREAT,
                title = "Known threat match",
                text = "$appName matches signed threat intelligence. Tap to see the " +
                    "evidence and available responses.",
                focusPackage = packageName,
                highPriority = true,
            )
        }.onFailure { Log.w(TAG, "Threat notification failed", it) }
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        focusPackage: String?,
        highPriority: Boolean,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.i(TAG, "Notifications disabled by the user; not posting '$title'")
            return
        }
        val tap = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            focusPackage?.let { putExtra(EXTRA_FOCUS_PACKAGE, it) }
        }
        val pending = PendingIntent.getActivity(
            context, id, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }.onFailure { Log.w(TAG, "notify() refused: ${it.message}") }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REVIEW, "Security review", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Capability-exposure findings that deserve a look. Not alarms."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_THREAT, "Threat alerts", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Signed threat-intelligence matches only."
            },
        )
    }

    /** Test-only reset. */
    internal fun resetForTest() {
        notifiedThreats.set(emptySet())
        lastReviewSet.set(emptySet())
    }
}
