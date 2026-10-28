package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExpenseTransaction
import com.example.data.MonthFinancialSummary
import com.example.ui.UiState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.DateUtils

@Composable
fun DashboardScreen(
    uiState: UiState,
    onSelectMonth: (String) -> Unit,
    onAdjustBalance: () -> Unit,
    onAddTransaction: () -> Unit,
    onVoiceEntry: () -> Unit,
    onScanReceipt: () -> Unit,
    onOpenChat: () -> Unit,
    onEditTransaction: (ExpenseTransaction) -> Unit,
    onDeleteTransaction: (ExpenseTransaction) -> Unit,
    onViewAllTransactions: () -> Unit,
    onOpenReceiptTextExtractor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val prevMonth = DateUtils.getPreviousMonthKey(uiState.selectedMonthKey)
    val nextMonth = DateUtils.getNextMonthKey(uiState.selectedMonthKey)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Selector Bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSelectMonth(prevMonth) },
                        modifier = Modifier.testTag("prev_month_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = DateUtils.formatDisplayMonth(uiState.selectedMonthKey),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Fiscal Month Period",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onSelectMonth(nextMonth) },
                        modifier = Modifier.testTag("next_month_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Hero Financial Health Card
        item {
            HeroBalanceCard(
                summary = uiState.summary,
                onAdjustStartingBalance = onAdjustBalance
            )
        }

        // Quick Input Shortcuts Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionPill(
                    title = "Add Entry",
                    icon = Icons.Default.Add,
                    color = EmeraldPrimary,
                    onClick = onAddTransaction,
                    modifier = Modifier.weight(1f)
                )

                QuickActionPill(
                    title = "Voice Log",
                    icon = Icons.Default.Mic,
                    color = Color(0xFF2563EB),
                    onClick = onVoiceEntry,
                    modifier = Modifier.weight(1f)
                )

                QuickActionPill(
                    title = "Scan Bill",
                    icon = Icons.Default.DocumentScanner,
                    color = Color(0xFF7C3AED),
                    onClick = onScanReceipt,
                    modifier = Modifier.weight(1f)
                )

                QuickActionPill(
                    title = "AI Chat",
                    icon = Icons.Default.Chat,
                    color = GoldAmber,
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Gemini Receipt & Log Extractor Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenReceiptTextExtractor() }
                    .testTag("dashboard_gemini_receipt_extractor_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gemini Receipt & Log Extractor",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = EmeraldPrimary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "AI API",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Extract vendor, amount & date from receipts or logs",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Spending Category Donut Chart
        item {
            CategoryDonutChartCard(categoryBreakdown = uiState.categoryBreakdown)
        }

        // Daily Spending Activity Bar Chart
        item {
            DailySpendingBarChartCard(dailySpends = uiState.dailySpendList)
        }

        // AI Financial Health & Trends Chart
        item {
            AiFinancialTrendsCard(
                summary = uiState.summary,
                categoryBreakdown = uiState.categoryBreakdown
            )
        }

        // Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(onClick = onViewAllTransactions) {
                    Text("View All (${uiState.transactions.size})")
                }
            }
        }

        // Transactions List preview (up to 4 items)
        if (uiState.transactions.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No records for ${DateUtils.formatDisplayMonth(uiState.selectedMonthKey)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap '+ Add Entry' or use Voice/Chat to record an expense",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.transactions.take(5), key = { it.id }) { tx ->
                TransactionItemCard(
                    transaction = tx,
                    onClick = { onEditTransaction(tx) },
                    onDelete = { onDeleteTransaction(tx) }
                )
            }
        }
    }
}

@Composable
fun QuickActionPill(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
