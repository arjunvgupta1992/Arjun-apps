package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MoneyLensApplication
import com.example.R
import com.example.data.repository.IngestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class MoneyLensNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Known Indian banking, UPI, fintech, and messaging package signatures
    private val ELIGIBLE_PACKAGE_KEYWORDS = listOf(
        "paisa", "gpay", "phonepe", "paytm", "bhim", "cred", "amazon",
        "hdfc", "sbi", "icici", "axis", "kotak", "pnb", "bob", "canara",
        "idfc", "indusind", "rbl", "yesbank", "federal", "jupiter", "fi",
        "messaging", "mms", "sms"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName?.lowercase(Locale.ROOT) ?: return

        // Fast check: Is this package an eligible fintech, bank, or SMS source?
        val isEligibleSource = ELIGIBLE_PACKAGE_KEYWORDS.any { packageName.contains(it) }
        if (!isEligibleSource) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val fullMessage = buildString {
            if (title.isNotBlank()) append(title).append(". ")
            if (bigText.isNotBlank()) append(bigText) else append(text)
        }.trim()

        if (fullMessage.isBlank()) return

        serviceScope.launch {
            try {
                val app = applicationContext as? MoneyLensApplication ?: return@launch
                val isMonitoring = app.preferencesManager.isMonitoringEnabled.firstOrNull() ?: true
                if (!isMonitoring) return@launch

                val result = app.transactionRepository.processAndIngest(
                    rawText = fullMessage,
                    packageName = packageName,
                    source = "NOTIFICATION",
                    timestamp = sbn.postTime
                )

                if (result is IngestResult.Success) {
                    showCaptureNotification(
                        context = applicationContext,
                        amount = result.parsed.amount,
                        merchant = result.parsed.merchant,
                        isDuplicate = result.isDuplicate
                    )
                }
            } catch (e: Exception) {
                // Keep service resilient against any transient parser issues
                e.printStackTrace()
            }
        }
    }

    private fun showCaptureNotification(
        context: Context,
        amount: Double,
        merchant: String,
        isDuplicate: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "moneylens_capture_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Transaction Captures",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new bank or UPI transaction is captured for confirmation"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "QUEUE")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isDuplicate) "⚠️ Potential Duplicate Detected" else "✨ Transaction Captured"
        val body = if (isDuplicate) {
            "Captured ₹$amount at $merchant. Flagged as potential duplicate in Queue."
        } else {
            "Recorded ₹$amount at $merchant. Tap to review in Confirmation Queue."
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
