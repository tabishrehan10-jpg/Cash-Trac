package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MonthFinancialSummary
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

@Composable
fun HeroBalanceCard(
    summary: MonthFinancialSummary?,
    onAdjustStartingBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_balance_card"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            EmeraldDark,
                            Color(0xFF0A332E),
                            NavyDeep
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top row with label and Adjust button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(EmeraldAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AVAILABLE BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    FilledTonalButton(
                        onClick = onAdjustStartingBalance,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0x33FFFFFF),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("adjust_balance_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Adjust", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Net Balance
                val netBalance = summary?.netBalance ?: 0.0
                Text(
                    text = CurrencyFormatter.formatInr(netBalance),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.testTag("net_balance_text")
                )

                // Rollover Indicator Badge (handles both surplus and negative deficit)
                val rolledOver = summary?.rolledOverAmount ?: 0.0
                if (rolledOver != 0.0) {
                    val isDeficit = rolledOver < 0
                    val badgeBg = if (isDeficit) Color(0x33EF4444) else Color(0x26F59E0B)
                    val iconTint = if (isDeficit) ExpenseRed else GoldAmber
                    val textCol = if (isDeficit) Color(0xFFFCA5A5) else Color(0xFFFDE68A)
                    val prefix = if (isDeficit) "-" else "+"
                    val label = if (isDeficit) "deficit rolled over from previous month" else "surplus rolled over from previous month"

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDeficit) Icons.Default.Warning else Icons.Default.Autorenew,
                                contentDescription = "Rolled Over",
                                tint = iconTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$prefix${CurrencyFormatter.formatInr(Math.abs(rolledOver))} $label",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = textCol
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Breakdown pills: Starting Bal, Income (+), Expense (-)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "Starting",
                        amount = summary?.startingBalance ?: 0.0,
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )

                    MetricMiniCard(
                        title = "Credits (+)",
                        amount = summary?.totalIncome ?: 0.0,
                        icon = Icons.Default.ArrowDownward,
                        accentColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )

                    MetricMiniCard(
                        title = "Debits (-)",
                        amount = summary?.totalExpense ?: 0.0,
                        icon = Icons.Default.ArrowUpward,
                        accentColor = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Budget Progress
                val limit = summary?.budgetLimit ?: 0.0
                if (limit > 0) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Monthly Budget Cap: ${CurrencyFormatter.formatInr(limit)}",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            val remaining = summary?.budgetRemaining ?: 0.0
                            Text(
                                text = if (remaining >= 0) "${CurrencyFormatter.formatInr(remaining)} left" else "Overspent by ${CurrencyFormatter.formatInr(-remaining)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remaining >= 0) IncomeGreen else ExpenseRed
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val progress = (summary?.budgetUsedPercentage ?: 0f) / 100f
                        val progressColor = when {
                            progress > 0.9f -> ExpenseRed
                            progress > 0.7f -> GoldAmber
                            else -> EmeraldAccent
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = progressColor,
                            trackColor = Color(0x33FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0x1AFFFFFF),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    fontSize = 10.5.sp,
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = CurrencyFormatter.formatCompact(amount),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
