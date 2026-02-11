package com.moneyprinter.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.moneyprinter.notifications.NotificationHelper

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)
        val lastSave = prefs.getLong("lastSave", System.currentTimeMillis())
        val hoursSince = (System.currentTimeMillis() - lastSave) / (1000 * 60 * 60)

        val helper = NotificationHelper(applicationContext)

        if (hoursSince >= 8) {
            helper.showOfflineEarningsNotification()
        }

        val lastLogin = prefs.getString("lastLoginDate", "") ?: ""
        if (lastLogin.isNotEmpty()) {
            helper.showDailyRewardNotification()
        }

        return Result.success()
    }
}
