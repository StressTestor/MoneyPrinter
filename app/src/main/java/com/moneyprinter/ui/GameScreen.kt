package com.moneyprinter.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyprinter.*
import com.moneyprinter.ads.AdManager
import com.moneyprinter.billing.BillingManager
import com.moneyprinter.billing.IAP_PRODUCTS
import com.moneyprinter.billing.IAPProduct
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

// ── Utilities ───────────────────────────────────────────────

fun formatMoney(amount: Double): String {
    if (amount < 1_000) return "$${String.format(Locale.US, "%.0f", amount)}"
    val suffixes = arrayOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")
    val tier = (log10(amount) / 3).toInt().coerceAtMost(suffixes.size - 1)
    val scaled = amount / 10.0.pow(tier * 3)
    return "$${String.format(Locale.US, "%.1f", scaled)}${suffixes[tier]}"
}

data class FloatingText(
    val id: Long,
    val text: String,
    val isLucky: Boolean,
)

// ── Main Screen ─────────────────────────────────────────────

@Composable
fun GameScreen(
    vm: GameViewModel,
    adManager: AdManager,
    billingManager: BillingManager,
    activity: Activity,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Daily reward dialog
    if (vm.hasPendingDailyReward) {
        DailyRewardDialog(vm)
    }

    // Offline earnings dialog
    if (vm.showOfflineDialog) {
        OfflineEarningsDialog(vm, adManager, activity)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MoneyHeader(vm, adManager, activity)

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
        ) {
            listOf("Generators", "Upgrades", "Store", "Prestige", "Stats").forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                    Text(title, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }

        when (selectedTab) {
            0 -> GeneratorsList(vm)
            1 -> UpgradesList(vm)
            2 -> StorePanel(billingManager, activity)
            3 -> PrestigePanel(vm)
            4 -> StatsPanel(vm)
        }
    }
}

// ── Money Header ────────────────────────────────────────────

@Composable
fun MoneyHeader(vm: GameViewModel, adManager: AdManager, activity: Activity) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }

    // Cleanup old floating texts
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (floatingTexts.size > 20) {
                floatingTexts.removeRange(0, floatingTexts.size - 10)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
                .padding(top = 48.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Money + Gems row
            Text(
                text = formatMoney(vm.money),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatMoney(vm.perSecond)}/sec",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B2838))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("\uD83D\uDC8E", fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${vm.gems}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00BCD4),
                    )
                }
                if (vm.investors > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B2838))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("\uD83D\uDC7C", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${vm.investors}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tap button
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF66BB6A), Color(0xFF2E7D32))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val result = vm.performTap()
                        floatingTexts.add(
                            FloatingText(
                                id = System.nanoTime(),
                                text = if (result.isLucky)
                                    "${result.multiplier}x LUCKY! +${formatMoney(result.amount)}"
                                else
                                    "+${formatMoney(result.amount)}",
                                isLucky = result.isLucky,
                            )
                        )
                        scope.launch {
                            scale.snapTo(if (result.isLucky) 0.8f else 0.9f)
                            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCB5", fontSize = 32.sp)
                    Text(
                        formatMoney(vm.effectiveTapValue),
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Boost button (ad-powered)
            if (!vm.adsRemoved) {
                Button(
                    onClick = {
                        adManager.showRewardedAd(activity, onRewarded = {
                            vm.activateBoost(30 * 60 * 1000L)
                        })
                    },
                    enabled = adManager.isRewardedAdReady() && !vm.isBoostActive,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (vm.isBoostActive) {
                        var remaining by remember { mutableLongStateOf(0L) }
                        LaunchedEffect(vm.boostEndTime) {
                            while (true) {
                                remaining = ((vm.boostEndTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                                if (remaining <= 0) break
                                delay(1000)
                            }
                        }
                        Text("\u26A1 2x ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", fontSize = 12.sp)
                    } else {
                        Text("\u26A1 2x Boost (30m) - Watch Ad", fontSize = 12.sp)
                    }
                }
            } else if (vm.isBoostActive) {
                var remaining by remember { mutableLongStateOf(0L) }
                LaunchedEffect(vm.boostEndTime) {
                    while (true) {
                        remaining = ((vm.boostEndTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                        if (remaining <= 0) break
                        delay(1000)
                    }
                }
                Text(
                    "\u26A1 2x Boost: ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}",
                    fontSize = 12.sp,
                    color = Color(0xFFFF9800),
                )
            }
        }

        // Floating text overlay
        floatingTexts.forEach { ft ->
            FloatingTextAnim(ft, onFinished = { floatingTexts.remove(ft) })
        }
    }
}

@Composable
fun FloatingTextAnim(ft: FloatingText, onFinished: () -> Unit) {
    var offsetY by remember { mutableIntStateOf(0) }
    var alpha by remember { mutableStateOf(1f) }

    LaunchedEffect(ft.id) {
        val startY = (100..200).random()
        val x = (50..300).random()
        repeat(30) { frame ->
            offsetY = startY - (frame * 5)
            alpha = 1f - (frame / 30f)
            delay(33)
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetY + 50, offsetY) }
            .alpha(alpha)
    ) {
        Text(
            text = ft.text,
            fontSize = if (ft.isLucky) 18.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (ft.isLucky) Color(0xFFFF9800) else Color(0xFF4CAF50),
        )
    }
}

// ── Generators Tab ──────────────────────────────────────────

@Composable
fun GeneratorsList(vm: GameViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(vm.generators) { index, gen ->
            GeneratorRow(gen, vm.money >= gen.price) { vm.buyGenerator(index) }
        }
    }
}

@Composable
fun GeneratorRow(gen: GeneratorState, canAfford: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(gen.generator.emoji, fontSize = 24.sp)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(gen.generator.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(gen.generator.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (gen.count > 0) {
                    Text("${formatMoney(gen.output)}/sec", fontSize = 12.sp, color = Color(0xFF4CAF50))
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("x${gen.count}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Button(
                    onClick = onBuy,
                    enabled = canAfford,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFF37474F),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(formatMoney(gen.price), fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Upgrades Tab ────────────────────────────────────────────

@Composable
fun UpgradesList(vm: GameViewModel) {
    val available = UPGRADES.filter { it.id !in vm.purchasedUpgrades }

    if (available.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("All upgrades purchased!\n\uD83C\uDFC6", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(available) { _, upgrade ->
            UpgradeRow(upgrade, vm.money >= upgrade.price) { vm.buyUpgrade(upgrade) }
        }
    }
}

@Composable
fun UpgradeRow(upgrade: Upgrade, canAfford: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canAfford) Color(0xFF1B3A2A) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(upgrade.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(upgrade.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(upgrade.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onBuy,
                enabled = canAfford,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF37474F),
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(formatMoney(upgrade.price), fontSize = 12.sp)
            }
        }
    }
}

// ── Store Tab ───────────────────────────────────────────────

@Composable
fun StorePanel(billingManager: BillingManager, activity: Activity) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(IAP_PRODUCTS) { _, product ->
            StoreCard(product) { billingManager.purchaseProduct(activity, product.id) }
        }
    }
}

@Composable
fun StoreCard(product: IAPProduct, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3A2A)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(product.emoji, fontSize = 40.sp, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(product.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onBuy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(product.priceLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Prestige Tab ────────────────────────────────────────────

@Composable
fun PrestigePanel(vm: GameViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Prestige info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (vm.canPrestige) Color(0xFF1B3A2A) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "\uD83D\uDC7C Investors: ${vm.investors}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                    Text(
                        "+${vm.investors}% Production",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    val gain = vm.calculateInvestorGain()
                    Text(
                        "Next Reset: +$gain Investors",
                        fontSize = 16.sp,
                        color = if (vm.canPrestige) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Lifetime: ${formatMoney(vm.lifetimeEarnings)} (need ${formatMoney(1e7)})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { vm.prestige() },
                        enabled = vm.canPrestige,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF37474F),
                        ),
                    ) {
                        Text("PRESTIGE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        // Prestige upgrades
        val available = PRESTIGE_UPGRADES.filter { it.id !in vm.purchasedPrestigeUpgrades }
        if (available.isNotEmpty()) {
            item {
                Text(
                    "Prestige Upgrades",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            itemsIndexed(available) { _, upgrade ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (vm.investors >= upgrade.investorCost) Color(0xFF1B3A2A) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(upgrade.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(upgrade.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(upgrade.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { vm.buyPrestigeUpgrade(upgrade) },
                            enabled = vm.investors >= upgrade.investorCost,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black,
                                disabledContainerColor = Color(0xFF37474F),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("${upgrade.investorCost} \uD83D\uDC7C", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Purchased prestige upgrades
        val purchased = PRESTIGE_UPGRADES.filter { it.id in vm.purchasedPrestigeUpgrades }
        if (purchased.isNotEmpty()) {
            item {
                Text(
                    "Owned",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            itemsIndexed(purchased) { _, upgrade ->
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(0.6f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(upgrade.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(upgrade.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(upgrade.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Stats Tab ───────────────────────────────────────────────

@Composable
fun StatsPanel(vm: GameViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val stats = listOf(
            "Lifetime Earned" to formatMoney(vm.lifetimeEarnings),
            "Current Cash" to formatMoney(vm.money),
            "Per Second" to "${formatMoney(vm.perSecond)}/sec",
            "Tap Value" to formatMoney(vm.effectiveTapValue),
            "Total Taps" to "${vm.totalTaps}",
            "Generators Owned" to "${vm.generators.sumOf { it.count }}",
            "Upgrades Bought" to "${vm.purchasedUpgrades.size}/${UPGRADES.size}",
            "Prestige Count" to "${vm.prestigeCount}",
            "Investors" to "${vm.investors} (+${vm.investors}%)",
            "Gems" to "\uD83D\uDC8E ${vm.gems}",
            "Daily Streak" to "\uD83D\uDD25 ${vm.dailyStreak} days",
            "Global Multiplier" to "${vm.globalMultiplier}x",
            "Investor Multiplier" to "${String.format(Locale.US, "%.2f", vm.investorMultiplier)}x",
        )
        itemsIndexed(stats) { _, (label, value) ->
            StatRow(label, value)
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// ── Dialogs ─────────────────────────────────────────────────

@Composable
fun DailyRewardDialog(vm: GameViewModel) {
    val rewardIndex = vm.dailyStreak % DAILY_REWARDS.size
    val reward = DAILY_REWARDS[rewardIndex]

    val (emoji, desc) = when (reward) {
        is DailyReward.Cash -> "\uD83D\uDCB5" to formatMoney(reward.amount)
        is DailyReward.Gems -> "\uD83D\uDC8E" to "${reward.amount} Gems"
        is DailyReward.Boost2x30Min -> "\u26A1" to "2x Earnings (30 min)"
        is DailyReward.MysteryBox -> "\uD83C\uDF81" to "Mystery Box!"
        is DailyReward.FreeGenerators -> "\uD83D\uDDA8\uFE0F" to "${reward.count} Free ${reward.generatorId}s"
    }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Daily Reward!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Day ${vm.dailyStreak + 1}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(emoji, fontSize = 64.sp, modifier = Modifier.padding(16.dp))
                Text(desc, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.claimDailyReward() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            ) {
                Text("CLAIM", fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun OfflineEarningsDialog(vm: GameViewModel, adManager: AdManager, activity: Activity) {
    AlertDialog(
        onDismissRequest = { vm.showOfflineDialog = false },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("\uD83D\uDCB0", fontSize = 48.sp)
                Text("Welcome Back!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "You earned ${formatMoney(vm.lastOfflineEarnings)} while away!",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                if (!vm.adsRemoved) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Watch an ad to double it!",
                        fontSize = 14.sp,
                        color = Color(0xFFFF9800),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            if (!vm.adsRemoved && adManager.isRewardedAdReady()) {
                Button(
                    onClick = {
                        adManager.showRewardedAd(activity, onRewarded = {
                            // Double the offline earnings (already got base amount, add another equal amount)
                            val bonus = vm.lastOfflineEarnings
                            vm.addOfflineBonus(bonus)
                            vm.showOfflineDialog = false
                        }, onFailed = {
                            vm.showOfflineDialog = false
                        })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                ) {
                    Text("WATCH AD (2x)", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showOfflineDialog = false }) {
                Text("Collect")
            }
        },
    )
}
