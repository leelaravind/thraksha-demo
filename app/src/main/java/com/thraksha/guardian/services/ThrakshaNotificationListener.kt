package com.thraksha.guardian.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.thraksha.guardian.security.evidence.CapabilityCatalog
import com.thraksha.guardian.security.evidence.CapabilityObservation
import com.thraksha.guardian.security.evidence.ObservationReliability
import com.thraksha.guardian.security.observe.RuntimeObservationStore

/**
 * Intercepts notifications for OTP detection and security monitoring.
 * Day 6 (Security Guardian) will wire regex OTP matching and phishing alerts here.
 */
class ThrakshaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Notification listener disconnected")
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        processNotification(sbn, posted = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        Log.v(TAG, "Notification removed: pkg=${sbn.packageName} key=${sbn.key}")
        processNotification(sbn, posted = false)
    }

    private fun processNotification(sbn: StatusBarNotification, posted: Boolean) {
        try {
            val packageName = sbn.packageName.orEmpty()
            if (packageName.isEmpty()) {
                Log.w(TAG, "Notification with empty package name, skipping")
                return
            }

            val content = parseNotificationContent(sbn.notification)
            if (posted) {
                Log.d(
                    TAG,
                    "Notification posted: pkg=$packageName title=${content.title} text=${content.text}",
                )
                // Phase 8.1: a notification delivered to this listener is genuine,
                // attributed Stage 3 evidence that the app posted a notification —
                // possible only while the user has enabled this listener. Deliberately
                // METADATA ONLY: package + timestamp; no title/text is recorded.
                RuntimeObservationStore.record(
                    CapabilityObservation(
                        capabilityId = CapabilityCatalog.NOTIFICATION_POSTING,
                        packageName = packageName,
                        observedAt = sbn.postTime,
                        source = "Thraksha notification listener (user-enabled)",
                        reliability = ObservationReliability.VERIFIED,
                        detail = "posted a notification (content not recorded)",
                    ),
                )
            }

            if (isMonitoredPackage(packageName)) {
                Log.d(TAG, "Monitored package notification: $packageName")
                analyzeMonitoredNotification(packageName, content)
            }

            // TODO Day 6: OTP pattern detection — match content.combinedText against OTP_REGEX_PATTERNS
            // if (containsOtp(content.combinedText)) {
            //     redactNotification(sbn)
            // }

            // TODO Day 6: Phishing detection — correlate packageName + text with threat intel
            // if (isSuspiciousNotification(packageName, content)) {
            //     alertSecurityGuardian(sbn, content)
            // }

            // TODO Day 6: Suspicious app notifications — flag unknown senders / overlay apps
            // if (isSuspiciousAppNotification(packageName, content)) {
            //     alertSecurityGuardian(sbn, content)
            // }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification from ${sbn.packageName}: ${e.message}", e)
        }
    }

    private fun analyzeMonitoredNotification(
        packageName: String,
        content: ParsedNotificationContent,
    ) {
        Log.d(
            TAG,
            "Analyzing monitored notification: pkg=$packageName combined=${content.combinedText.take(80)}",
        )
        // Day 6: prioritized OTP / banking pipeline for MONITORED_PACKAGES
    }

    private fun parseNotificationContent(notification: Notification?): ParsedNotificationContent {
        if (notification == null) {
            return ParsedNotificationContent.EMPTY
        }

        return try {
            val extras = notification.extras ?: return ParsedNotificationContent.EMPTY
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()

            ParsedNotificationContent(
                title = title,
                text = text,
                bigText = bigText,
                subText = subText,
                infoText = infoText,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse notification extras: ${e.message}", e)
            ParsedNotificationContent.EMPTY
        }
    }

    // region Day 6 placeholders

    @Suppress("unused")
    private fun containsOtp(text: String?): Boolean {
        // TODO Day 6: apply OTP_REGEX_PATTERNS
        return false
    }

    @Suppress("unused")
    private fun redactNotification(sbn: StatusBarNotification) {
        // TODO Day 6: cancel or snooze notification and notify Security Guardian
        Log.d(TAG, "redactNotification placeholder: key=${sbn.key}")
    }

    @Suppress("unused")
    private fun isSuspiciousNotification(
        packageName: String,
        content: ParsedNotificationContent,
    ): Boolean {
        // TODO Day 6: phishing heuristics
        return false
    }

    @Suppress("unused")
    private fun isSuspiciousAppNotification(
        packageName: String,
        content: ParsedNotificationContent,
    ): Boolean {
        // TODO Day 6: suspicious app / impersonation checks
        return false
    }

    @Suppress("unused")
    private fun alertSecurityGuardian(
        sbn: StatusBarNotification,
        content: ParsedNotificationContent,
    ) {
        // TODO Day 6: push alert to Security Guardian backend / UI
        Log.d(TAG, "alertSecurityGuardian placeholder: pkg=${sbn.packageName}")
    }

    // endregion

    data class ParsedNotificationContent(
        val title: String,
        val text: String,
        val bigText: String,
        val subText: String,
        val infoText: String,
    ) {
        val combinedText: String
            get() = listOf(title, text, bigText, subText, infoText)
                .filter { it.isNotBlank() }
                .joinToString(separator = " | ")

        companion object {
            val EMPTY = ParsedNotificationContent("", "", "", "", "")
        }
    }

    companion object {
        private const val TAG = "ThrakshaNotifListener"

        /**
         * Packages prioritized for OTP / banking monitoring (expand on Day 6).
         */
        val MONITORED_PACKAGES: Set<String> = setOf(
            "com.google.android.apps.authenticator2",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )

        /**
         * Day 6: OTP regex patterns (e.g. 4–8 digit codes, "OTP", "verification code").
         */
        val OTP_REGEX_PATTERNS: List<String> = emptyList()

        @Volatile
        var instance: ThrakshaNotificationListener? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun isMonitoredPackage(packageName: String): Boolean =
            packageName in MONITORED_PACKAGES

        /**
         * Returns display-friendly summary for logging or Security Guardian payloads.
         */
        fun summarizeContent(content: ParsedNotificationContent): String {
            if (content === ParsedNotificationContent.EMPTY) return "(empty)"
            return buildString {
                if (content.title.isNotBlank()) append("title=").append(content.title).append(' ')
                if (content.text.isNotBlank()) append("text=").append(content.text).append(' ')
                if (content.bigText.isNotBlank()) append("bigText=").append(content.bigText.take(64))
            }.trim()
        }
    }
}
