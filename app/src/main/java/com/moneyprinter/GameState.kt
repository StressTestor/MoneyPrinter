package com.moneyprinter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val multiplierTarget: String, // "tap" or generator id
    val multiplier: Double,
)

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

class GameViewModel(private val context: Context) : ViewModel() {
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

    val perSecond: Double
        get() = generators.sumOf { it.output } * globalMultiplier

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
            }
        }
        // Auto-save every 30s
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                save()
            }
        }
    }

    fun tap() {
        money += tapValue * globalMultiplier
        totalEarned += tapValue * globalMultiplier
        totalTaps++
    }

    fun buyGenerator(index: Int) {
        val gen = generators[index]
        if (money >= gen.price) {
            money -= gen.price
            generators[index] = gen.copy(count = gen.count + 1)
        }
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

    fun save() {
        val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE).edit()
        prefs.putLong("money", money.toLong())
        prefs.putLong("totalEarned", totalEarned.toLong())
        prefs.putFloat("tapValue", tapValue.toFloat())
        prefs.putFloat("globalMultiplier", globalMultiplier.toFloat())
        prefs.putInt("totalTaps", totalTaps)
        prefs.putLong("lastSave", System.currentTimeMillis())
        generators.forEachIndexed { i, g -> prefs.putInt("gen_$i", g.count) }
        prefs.putStringSet("upgrades", purchasedUpgrades.toSet())
        prefs.apply()
    }

    fun load() {
        val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)
        if (!prefs.contains("money")) return

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

        // Reapply upgrade multipliers
        saved.forEach { id ->
            UPGRADES.find { it.id == id }?.let { upgrade ->
                when (upgrade.multiplierTarget) {
                    "tap", "all" -> {} // Already stored in tapValue/globalMultiplier
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

        // Offline earnings
        val lastSave = prefs.getLong("lastSave", System.currentTimeMillis())
        val elapsed = (System.currentTimeMillis() - lastSave) / 1000.0
        if (elapsed > 5) {
            val offlineEarned = perSecond * elapsed * 0.5 // 50% offline rate
            money += offlineEarned
            totalEarned += offlineEarned
        }
    }
}

class GameViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(context.applicationContext) as T
    }
}
