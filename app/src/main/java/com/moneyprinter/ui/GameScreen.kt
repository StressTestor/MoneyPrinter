package com.moneyprinter.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyprinter.GameViewModel
import com.moneyprinter.GeneratorState
import com.moneyprinter.UPGRADES
import com.moneyprinter.Upgrade
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun formatMoney(amount: Double): String {
    if (amount < 1_000) return "$${String.format(Locale.US, "%.0f", amount)}"
    val suffixes = arrayOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")
    val tier = (log10(amount) / 3).toInt().coerceAtMost(suffixes.size - 1)
    val scaled = amount / 10.0.pow(tier * 3)
    return "$${String.format(Locale.US, "%.1f", scaled)}${suffixes[tier]}"
}

@Composable
fun GameScreen(vm: GameViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Money display header
        MoneyHeader(vm)

        // Tab row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Generators", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Upgrades", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("Stats", modifier = Modifier.padding(12.dp))
            }
        }

        // Content
        when (selectedTab) {
            0 -> GeneratorsList(vm)
            1 -> UpgradesList(vm)
            2 -> StatsPanel(vm)
        }
    }
}

@Composable
fun MoneyHeader(vm: GameViewModel) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

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
            .padding(top = 48.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatMoney(vm.money),
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
        )
        Text(
            text = "${formatMoney(vm.perSecond)}/sec",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Tap button
        Box(
            modifier = Modifier
                .size(120.dp)
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
                    vm.tap()
                    scope.launch {
                        scale.snapTo(0.9f)
                        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("\uD83D\uDCB5", fontSize = 36.sp)
                Text(
                    formatMoney(vm.tapValue * vm.globalMultiplier),
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

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
            // Emoji + count
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
                Text(
                    gen.generator.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    gen.generator.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (gen.count > 0) {
                    Text(
                        "${formatMoney(gen.output)}/sec",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "x${gen.count}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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

@Composable
fun UpgradesList(vm: GameViewModel) {
    val available = UPGRADES.filter { it.id !in vm.purchasedUpgrades }

    if (available.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "All upgrades purchased!\n\uD83C\uDFC6",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(upgrade.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    upgrade.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    upgrade.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
fun StatsPanel(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatRow("Total Earned", formatMoney(vm.totalEarned))
        StatRow("Total Taps", "${vm.totalTaps}")
        StatRow("Tap Value", formatMoney(vm.tapValue * vm.globalMultiplier))
        StatRow("Per Second", "${formatMoney(vm.perSecond)}/sec")
        StatRow("Global Multiplier", "${vm.globalMultiplier}x")
        StatRow("Generators Owned", "${vm.generators.sumOf { it.count }}")
        StatRow("Upgrades Bought", "${vm.purchasedUpgrades.size}/${UPGRADES.size}")
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
