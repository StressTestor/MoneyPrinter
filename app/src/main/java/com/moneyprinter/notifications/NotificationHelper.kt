package com.moneyprinter.notifications

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moneyprinter.MainActivity

class NotificationHelper(private val context: Context) {

    private val channelId = "money_printer_channel"

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "Money Printer Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Offline earnings and daily reward alerts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showOfflineEarningsNotification() {
        if (!hasPermission()) return
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Offline Earnings Full!")
            .setContentText("Your printers are idle. Come collect!")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }

    fun showDailyRewardNotification() {
        if (!hasPermission()) return
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Daily Reward Ready!")
            .setContentText("Your daily reward is waiting to be claimed.")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(2, notification)
    }

    private fun hasPermission(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
    }
}
