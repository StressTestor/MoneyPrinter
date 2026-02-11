package com.moneyprinter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moneyprinter.ads.AdManager
import com.moneyprinter.billing.BillingManager
import com.moneyprinter.notifications.NotificationHelper
import com.moneyprinter.ui.GameScreen
import com.moneyprinter.ui.theme.MoneyPrinterTheme
import com.moneyprinter.workers.NotificationWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var vm: GameViewModel
    private lateinit var adManager: AdManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        vm = ViewModelProvider(this, GameViewModelFactory(this))[GameViewModel::class.java]
        adManager = AdManager(this)
        billingManager = BillingManager(this) { productId ->
            vm.handleIAPPurchase(productId)
        }

        vm.checkDailyReward()

        // Notification permission + scheduled checks
        NotificationHelper(this).requestPermission(this)
        scheduleNotifications()

        setContent {
            MoneyPrinterTheme {
                GameScreen(vm, adManager, billingManager, this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vm.save()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
    }

    private fun scheduleNotifications() {
        val work = PeriodicWorkRequestBuilder<NotificationWorker>(
            8, TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "money_printer_notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            work,
        )
    }
}
