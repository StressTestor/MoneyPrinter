package com.moneyprinter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.sqrt

// ── Data Models ─────────────────────────────────────────────

data class Generator(
    val id: String,
    val name: String,
    val emoji: String,
    val basePrice: Double,
    val baseOutput: Double,
    val description: String,
)

data class GeneratorState(
    val generator: Generator,
    var count: Int = 0,
) {
    val price: Double get() = generator.basePrice * Math.pow(1.15, count.toDouble())
    val output: Double get() = generator.baseOutput * count
}

data class Upgrade(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Double,
    val description: String,
    val multiplierTarget: String,
    val multiplier: Double,
)

data class PrestigeUpgrade(
    val id: String,
    val name: String,
    val emoji: String,
    val investorCost: Int,
    val description: String,
    val multiplier: Double,
    val target: String, // "tap", "all", "offline"
)

data class TapResult(
    val amount: Double,
    val isLucky: Boolean,
    val multiplier: Int,
    val gemsEarned: Int,
)

sealed class DailyReward {
    data class Cash(val amount: Double) : DailyReward()
    data class Gems(val amount: Int) : DailyReward()
    object Boost2x30Min : DailyReward()
    object MysteryBox : DailyReward()
    data class FreeGenerators(val generatorId: String, val count: Int) : DailyReward()
}

// ── Constants ───────────────────────────────────────────────

val GENERATORS = listOf(
    Generator("intern", "Unpaid Intern", "\uD83D\uDC68\u200D\uD83D\uDCBB", 15.0, 0.1, "Counts pennies"),
    Generator("printer", "Money Printer", "\uD83D\uDDA8\uFE0F", 100.0, 1.0, "Brrrrr"),
    Generator("bank", "Shady Bank", "\uD83C\uDFE6", 1_100.0, 8.0, "Don't ask questions"),
    Generator("stonks", "Stonks Trader", "\uD83D\uDCC8", 12_000.0, 47.0, "Buys high, sells higher somehow"),
    Generator("vault", "Gold Vault", "\uD83E\uDD47", 130_000.0, 260.0, "Fort Knox who?"),
    Generator("rocket", "Crypto Rocket", "\uD83D\uDE80", 1_400_000.0, 1_400.0, "To the moon"),
    Generator("wizard", "Finance Wizard", "\uD83E\uDDD9", 20_000_000.0, 7_800.0, "Literally magic"),
    Generator("hole", "Black Hole Fund", "\uD83C\uDF11", 330_000_000.0, 44_000.0, "Money goes in, more comes out"),
    Generator("god", "Money God", "\uD83D\uDC51", 5_100_000_000.0, 260_000.0, "Transcended economics"),
)

val UPGRADES = listOf(
    Upgrade("tap1", "Bigger Fingers", "\uD83D\uDC46", 100.0, "2x tap value", "tap", 2.0),
    Upgrade("tap2", "Golden Touch", "\u2728", 5_000.0, "3x tap value", "tap", 3.0),
    Upgrade("tap3", "Midas Mode", "\uD83C\uDFC6", 500_000.0, "5x tap value", "tap", 5.0),
    Upgrade("intern1", "Coffee Machine", "\u2615", 1_000.0, "2x intern output", "intern", 2.0),
    Upgrade("printer1", "Ink Refill", "\uD83D\uDDA8\uFE0F", 5_000.0, "2x printer output", "printer", 2.0),
    Upgrade("bank1", "Offshore Account", "\uD83C\uDFDD\uFE0F", 50_000.0, "2x bank output", "bank", 2.0),
    Upgrade("stonks1", "Insider Info", "\uD83E\uDD2B", 500_000.0, "2x stonks output", "stonks", 2.0),
    Upgrade("vault1", "Better Locks", "\uD83D\uDD12", 5_000_000.0, "2x vault output", "vault", 2.0),
    Upgrade("rocket1", "Diamond Hands", "\uD83D\uDC8E", 50_000_000.0, "2x rocket output", "rocket", 2.0),
    Upgrade("global1", "Quantitative Easing", "\uD83C\uDF0D", 10_000_000.0, "2x ALL output", "all", 2.0),
    Upgrade("global2", "Infinite Leverage", "\u267E\uFE0F", 1_000_000_000.0, "5x ALL output", "all", 5.0),
)

val PRESTIGE_UPGRADES = listOf(
    PrestigeUpgrade("p_tap1", "Angel Investors", "\uD83D\uDC7C", 10, "2x tap value (permanent)", 2.0, "tap"),
    PrestigeUpgrade("p_global1", "VC Funding", "\uD83D\uDE80", 25, "1.5x ALL production", 1.5, "all"),
    PrestigeUpgrade("p_offline1", "Automated Trading", "\uD83E\uDD16", 50, "Offline rate 50% \u2192 75%", 1.5, "offline"),
    PrestigeUpgrade("p_global2", "Market Dominance", "\uD83D\uDC51", 100, "2x ALL production", 2.0, "all"),
    PrestigeUpgrade("p_tap2", "Diamond Fingers", "\uD83D\uDC8E", 150, "3x tap value (permanent)", 3.0, "tap"),
    PrestigeUpgrade("p_global3", "Economic Singularity", "\u267E\uFE0F", 250, "3x ALL production", 3.0, "all"),
)

val DAILY_REWARDS = listOf(
    DailyReward.Cash(1_000.0),
    DailyReward.Gems(10),
    DailyReward.Boost2x30Min,
    DailyReward.Gems(25),
    DailyReward.MysteryBox,
    DailyReward.Gems(50),
    DailyReward.FreeGenerators("printer", 5),
)

// ── ViewModel ───────────────────────────────────────────────

class GameViewModel(private val context: Context) : ViewModel() {

    // Core state
    var money by mutableDoubleStateOf(0.0)
        private set
    var totalEarned by mutableDoubleStateOf(0.0)
        private set
    var tapValue by mutableDoubleStateOf(1.0)
        private set
    var globalMultiplier by mutableDoubleStateOf(1.0)
        private set
    var totalTaps by mutableIntStateOf(0)
        private set

    val generators = mutableStateListOf<GeneratorState>()
    val purchasedUpgrades = mutableStateListOf<String>()

    // Prestige
    var investors by mutableIntStateOf(0)
        private set
    var lifetimeEarnings by mutableDoubleStateOf(0.0)
        private set
    var prestigeCount by mutableIntStateOf(0)
        private set
    val purchasedPrestigeUpgrades = mutableStateListOf<String>()

    // Gems
    var gems by mutableIntStateOf(0)
        private set

    // Boost
    var boostEndTime by mutableLongStateOf(0L)
        private set

    // Daily rewards
    var dailyStreak by mutableIntStateOf(0)
        private set
    var lastLoginDate by mutableStateOf("")
        private set
    var hasPendingDailyReward by mutableStateOf(false)
        private set

    // Offline earnings
    var lastOfflineEarnings by mutableDoubleStateOf(0.0)
        private set
    var showOfflineDialog by mutableStateOf(false)

    // IAP
    var adsRemoved by mutableStateOf(false)
        private set

    // Offline rate (can be upgraded via prestige)
    private val offlineRate: Double
        get() {
            var rate = 0.5
            if ("p_offline1" in purchasedPrestigeUpgrades) rate = 0.75
            return rate
        }

    // ── Computed Properties ─────────────────────────────────

    val investorMultiplier: Double
        get() {
            var mult = 1.0 + (investors * 0.01)
            purchasedPrestigeUpgrades.forEach { id ->
                PRESTIGE_UPGRADES.find { it.id == id }?.let { pu ->
                    if (pu.target == "all") mult *= pu.multiplier
                }
            }
            return mult
        }

    private val prestigeTapMultiplier: Double
        get() {
            var mult = 1.0
            purchasedPrestigeUpgrades.forEach { id ->
                PRESTIGE_UPGRADES.find { it.id == id }?.let { pu ->
                    if (pu.target == "tap") mult *= pu.multiplier
                }
            }
            return mult
        }

    val isBoostActive: Boolean
        get() = System.currentTimeMillis() < boostEndTime

    val boostMultiplier: Double
        get() = if (isBoostActive) 2.0 else 1.0

    val perSecond: Double
        get() = generators.sumOf { it.output } * globalMultiplier * investorMultiplier * boostMultiplier

    val effectiveTapValue: Double
        get() = tapValue * globalMultiplier * investorMultiplier * prestigeTapMultiplier * boostMultiplier

    val canPrestige: Boolean
        get() = lifetimeEarnings >= 1e7 && calculateInvestorGain() > 0

    fun calculateInvestorGain(): Int {
        return floor(sqrt(lifetimeEarnings / 1e9)).toInt()
    }

    // ── Init ────────────────────────────────────────────────

    init {
        GENERATORS.forEach { generators.add(GeneratorState(it)) }
        load()
        startTicking()
    }

    private fun startTicking() {
        viewModelScope.launch {
            while (true) {
                delay(100)
                val earned = perSecond / 10.0
                money += earned
                totalEarned += earned
                lifetimeEarnings += earned
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                save()
            }
        }
    }

    // ── Actions ─────────────────────────────────────────────

    fun performTap(): TapResult {
        val baseAmount = effectiveTapValue
        val isLucky = (1..100).random() <= 7
        val multiplier = if (isLucky) (2..10).random() else 1
        val earnAmount = baseAmount * multiplier

        money += earnAmount
        totalEarned += earnAmount
        lifetimeEarnings += earnAmount
        totalTaps++

        val gemsEarned = if (isLucky && (1..100).random() <= 5) 1 else 0
        if (gemsEarned > 0) gems += gemsEarned

        return TapResult(earnAmount, isLucky, multiplier, gemsEarned)
    }

    fun buyGenerator(index: Int) {
        val gen = generators[index]
        if (money >= gen.price) {
            money -= gen.price
            generators[index] = gen.copy(count = gen.count + 1)
        }
    }

    fun addFreeGenerator(index: Int) {
        val gen = generators[index]
        generators[index] = gen.copy(count = gen.count + 1)
    }

    fun buyUpgrade(upgrade: Upgrade) {
        if (money >= upgrade.price && upgrade.id !in purchasedUpgrades) {
            money -= upgrade.price
            purchasedUpgrades.add(upgrade.id)
            when (upgrade.multiplierTarget) {
                "tap" -> tapValue *= upgrade.multiplier
                "all" -> globalMultiplier *= upgrade.multiplier
                else -> {
                    val idx = generators.indexOfFirst { it.generator.id == upgrade.multiplierTarget }
                    if (idx >= 0) {
                        val gen = generators[idx]
                        generators[idx] = GeneratorState(
                            gen.generator.copy(baseOutput = gen.generator.baseOutput * upgrade.multiplier),
                            gen.count
                        )
                    }
                }
            }
        }
    }

    // ── Prestige ────────────────────────────────────────────

    fun prestige() {
        if (!canPrestige) return

        val newInvestors = calculateInvestorGain()
        investors += newInvestors
        prestigeCount++

        // Reset volatile state
        money = 0.0
        totalEarned = 0.0
        tapValue = 1.0
        globalMultiplier = 1.0
        totalTaps = 0
        boostEndTime = 0L

        // Reset generators
        generators.forEachIndexed { i, _ ->
            generators[i] = GeneratorState(GENERATORS[i])
        }

        // Reset non-prestige upgrades
        purchasedUpgrades.clear()

        // lifetimeEarnings, investors, gems, prestige upgrades, adsRemoved persist
        save(critical = true)
    }

    fun buyPrestigeUpgrade(upgrade: PrestigeUpgrade) {
        if (investors >= upgrade.investorCost && upgrade.id !in purchasedPrestigeUpgrades) {
            investors -= upgrade.investorCost
            purchasedPrestigeUpgrades.add(upgrade.id)
            save(critical = true)
        }
    }

    // ── Gems ────────────────────────────────────────────────

    fun addGems(amount: Int) {
        gems += amount
    }

    fun spendGems(amount: Int): Boolean {
        if (gems >= amount) {
            gems -= amount
            return true
        }
        return false
    }

    // ── Boost ───────────────────────────────────────────────

    fun activateBoost(durationMs: Long) {
        boostEndTime = System.currentTimeMillis() + durationMs
    }

    fun addOfflineBonus(amount: Double) {
        money += amount
        totalEarned += amount
        lifetimeEarnings += amount
    }

    // ── Daily Rewards ───────────────────────────────────────

    fun checkDailyReward() {
        val today = getCurrentDateUTC()

        if (lastLoginDate.isEmpty()) {
            lastLoginDate = today
            dailyStreak = 0
            hasPendingDailyReward = true
            save()
            return
        }

        if (lastLoginDate == today) {
            hasPendingDailyReward = false
            return
        }

        val diffDays = daysBetween(lastLoginDate, today)
        if (diffDays == 1) {
            dailyStreak++
        } else if (diffDays > 1) {
            dailyStreak = 0
        }
        lastLoginDate = today
        hasPendingDailyReward = true
        save()
    }

    fun claimDailyReward() {
        if (!hasPendingDailyReward) return

        val rewardIndex = dailyStreak % DAILY_REWARDS.size
        val reward = DAILY_REWARDS[rewardIndex]

        when (reward) {
            is DailyReward.Cash -> {
                money += reward.amount
                totalEarned += reward.amount
                lifetimeEarnings += reward.amount
            }
            is DailyReward.Gems -> addGems(reward.amount)
            is DailyReward.Boost2x30Min -> activateBoost(30 * 60 * 1000L)
            is DailyReward.MysteryBox -> {
                when ((0..99).random()) {
                    in 0..49 -> {
                        val cash = (1000..10000).random().toDouble()
                        money += cash
                        totalEarned += cash
                        lifetimeEarnings += cash
                    }
                    in 50..79 -> addGems((10..50).random())
                    else -> {
                        val genIdx = generators.indices.random()
                        addFreeGenerator(genIdx)
                    }
                }
            }
            is DailyReward.FreeGenerators -> {
                val idx = generators.indexOfFirst { it.generator.id == reward.generatorId }
                if (idx >= 0) repeat(reward.count) { addFreeGenerator(idx) }
            }
        }

        hasPendingDailyReward = false
        save()
    }

    private fun getCurrentDateUTC(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun daysBetween(dateA: String, dateB: String): Int {
        return try {
            val a = parseDateMs(dateA)
            val b = parseDateMs(dateB)
            ((b - a) / (1000 * 60 * 60 * 24)).toInt()
        } catch (_: Exception) {
            0
        }
    }

    private fun parseDateMs(dateStr: String): Long {
        val parts = dateStr.split("-")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ── IAP ─────────────────────────────────────────────────

    fun handleIAPPurchase(productId: String) {
        when (productId) {
            "starter_pack" -> {
                addGems(500)
                activateBoost(24 * 60 * 60 * 1000L)
                money += 1_000_000.0
                totalEarned += 1_000_000.0
                lifetimeEarnings += 1_000_000.0
            }
            "remove_ads" -> adsRemoved = true
            "gems_small" -> addGems(100)
            "gems_medium" -> addGems(300)
            "gems_large" -> addGems(1000)
        }
        save(critical = true)
    }

    // ── Save / Load ─────────────────────────────────────────

    fun save(critical: Boolean = false) {
        val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE).edit()

        // Core
        prefs.putLong("money", money.toLong())
        prefs.putLong("totalEarned", totalEarned.toLong())
        prefs.putFloat("tapValue", tapValue.toFloat())
        prefs.putFloat("globalMultiplier", globalMultiplier.toFloat())
        prefs.putInt("totalTaps", totalTaps)
        prefs.putLong("lastSave", System.currentTimeMillis())
        generators.forEachIndexed { i, g -> prefs.putInt("gen_$i", g.count) }
        prefs.putStringSet("upgrades", purchasedUpgrades.toSet())

        // Prestige
        prefs.putInt("investors", investors)
        prefs.putLong("lifetimeEarnings", lifetimeEarnings.toLong())
        prefs.putInt("prestigeCount", prestigeCount)
        prefs.putStringSet("prestigeUpgrades", purchasedPrestigeUpgrades.toSet())

        // Gems & Boost
        prefs.putInt("gems", gems)
        prefs.putLong("boostEndTime", boostEndTime)

        // Daily
        prefs.putInt("dailyStreak", dailyStreak)
        prefs.putString("lastLoginDate", lastLoginDate)
        prefs.putBoolean("hasPendingDailyReward", hasPendingDailyReward)

        // IAP
        prefs.putBoolean("adsRemoved", adsRemoved)

        if (critical) prefs.commit() else prefs.apply()
    }

    fun load() {
        val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)
        if (!prefs.contains("money")) return

        // Core
        money = prefs.getLong("money", 0).toDouble()
        totalEarned = prefs.getLong("totalEarned", 0).toDouble()
        tapValue = prefs.getFloat("tapValue", 1f).toDouble()
        globalMultiplier = prefs.getFloat("globalMultiplier", 1f).toDouble()
        totalTaps = prefs.getInt("totalTaps", 0)

        generators.forEachIndexed { i, g ->
            generators[i] = g.copy(count = prefs.getInt("gen_$i", 0))
        }

        val saved = prefs.getStringSet("upgrades", emptySet()) ?: emptySet()
        purchasedUpgrades.addAll(saved)

        // Reapply generator-specific upgrade multipliers
        saved.forEach { id ->
            UPGRADES.find { it.id == id }?.let { upgrade ->
                when (upgrade.multiplierTarget) {
                    "tap", "all" -> {} // Already in tapValue/globalMultiplier
                    else -> {
                        val idx = generators.indexOfFirst { it.generator.id == upgrade.multiplierTarget }
                        if (idx >= 0) {
                            val gen = generators[idx]
                            generators[idx] = GeneratorState(
                                gen.generator.copy(baseOutput = gen.generator.baseOutput * upgrade.multiplier),
                                gen.count
                            )
                        }
                    }
                }
            }
        }

        // Prestige (with migration defaults)
        investors = prefs.getInt("investors", 0)
        lifetimeEarnings = prefs.getLong("lifetimeEarnings", 0).toDouble()
            .coerceAtLeast(totalEarned) // Migration: ensure lifetime >= total
        prestigeCount = prefs.getInt("prestigeCount", 0)
        val prestigeSaved = prefs.getStringSet("prestigeUpgrades", emptySet()) ?: emptySet()
        purchasedPrestigeUpgrades.addAll(prestigeSaved)

        // Gems & Boost
        gems = prefs.getInt("gems", 0)
        boostEndTime = prefs.getLong("boostEndTime", 0L)

        // Daily
        dailyStreak = prefs.getInt("dailyStreak", 0)
        lastLoginDate = prefs.getString("lastLoginDate", "") ?: ""
        hasPendingDailyReward = prefs.getBoolean("hasPendingDailyReward", false)

        // IAP
        adsRemoved = prefs.getBoolean("adsRemoved", false)

        // Offline earnings (capped at 8 hours)
        val lastSave = prefs.getLong("lastSave", System.currentTimeMillis())
        val elapsed = ((System.currentTimeMillis() - lastSave) / 1000.0).coerceAtMost(8.0 * 3600.0)
        if (elapsed > 5) {
            val offlineEarned = perSecond * elapsed * offlineRate
            lastOfflineEarnings = offlineEarned
            money += offlineEarned
            totalEarned += offlineEarned
            lifetimeEarnings += offlineEarned
            if (elapsed > 3600 && offlineEarned > 0) {
                showOfflineDialog = true
            }
        }
    }
}

class GameViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(context.applicationContext) as T
    }
}
