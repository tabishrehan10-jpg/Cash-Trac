package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Categories
import com.example.data.CategorySpend
import com.example.data.DailySpend
import com.example.data.MonthFinancialSummary
import com.example.data.TransactionType
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import java.util.Calendar
import java.util.Locale

@Composable
fun CategoryDonutChartCard(
    categoryBreakdown: List<CategorySpend>,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<CategorySpend?>(null) }
    var viewMode by remember { mutableStateOf(0) } // 0: Donut Wheel, 1: Ranked Bars
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(categoryBreakdown) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val totalAmount = categoryBreakdown.sumOf { it.totalAmount }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chart_category_donut_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Category Breakdown",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Interactive spending distribution by category",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // View Toggle (Donut vs Ranked List)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (viewMode == 0) EmeraldPrimary else Color.Transparent)
                                .clickable { viewMode = 0 }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Wheel",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewMode == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (viewMode == 1) EmeraldPrimary else Color.Transparent)
                                .clickable { viewMode = 1 }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Ranked",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (categoryBreakdown.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expenses recorded this month yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewMode == 0) {
                // DONUT WHEEL VIEW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Enhanced Donut Canvas with Animated Sweep
                    Box(
                        modifier = Modifier.size(154.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(144.dp)) {
                            val strokeWidth = 26f
                            val diameter = size.minDimension - strokeWidth
                            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                            val arcSize = Size(diameter, diameter)

                            // Background guide ring
                            drawArc(
                                color = Color(0xFFE2E8F0).copy(alpha = 0.45f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth)
                            )

                            var startAngle = -90f
                            val progress = animationProgress.value
                            for (cat in categoryBreakdown) {
                                val meta = Categories.getCategoryMeta(cat.category, TransactionType.EXPENSE)
                                val sweep = ((cat.percentage / 100f) * 360f) * progress
                                val isSelected = selectedCategory == cat
                                val sliceStroke = if (isSelected) strokeWidth + 6f else strokeWidth

                                if (sweep > 0f) {
                                    drawArc(
                                        color = meta.color,
                                        startAngle = startAngle,
                                        sweepAngle = (sweep - 2.5f).coerceAtLeast(0.5f),
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = sliceStroke, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep
                                }
                            }
                        }

                        // Center Information Pod
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            val display = selectedCategory
                            if (display != null) {
                                val meta = Categories.getCategoryMeta(display.category, TransactionType.EXPENSE)
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    tint = meta.color,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = display.category,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = CurrencyFormatter.formatCompact(display.totalAmount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = meta.color
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", display.percentage)}%",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Total Spend",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatCompact(totalAmount),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tap to inspect",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Top Categories List with Progress Bars
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryBreakdown.take(4).forEach { cat ->
                            val meta = Categories.getCategoryMeta(cat.category, TransactionType.EXPENSE)
                            val isSelected = selectedCategory == cat

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) meta.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategory = if (isSelected) null else cat
                                    }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(meta.color)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = cat.category,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }

                                        Text(
                                            text = CurrencyFormatter.formatCompact(cat.totalAmount),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    LinearProgressIndicator(
                                        progress = { (cat.percentage / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = meta.color,
                                        trackColor = Color(0xFFE2E8F0).copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // RANKED LIST VIEW
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryBreakdown.forEachIndexed { index, cat ->
                        val meta = Categories.getCategoryMeta(cat.category, TransactionType.EXPENSE)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = meta.color.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = meta.color,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = meta.icon,
                                            contentDescription = null,
                                            tint = meta.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat.category,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = CurrencyFormatter.formatCurrency(cat.totalAmount),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", cat.percentage)}% (${cat.count} txns)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { (cat.percentage / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = meta.color,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Spending Insight Callout
            val highest = categoryBreakdown.maxByOrNull { it.totalAmount }
            if (highest != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val insightText = if (highest.percentage >= 40f) {
                            "${highest.category} accounts for ${String.format(Locale.US, "%.1f", highest.percentage)}% of your expenses. Setting a weekly cap can save up to ${CurrencyFormatter.formatCompact(highest.totalAmount * 0.15)}."
                        } else {
                            "Healthy distribution: Top outflow is ${highest.category} at ${String.format(Locale.US, "%.1f", highest.percentage)}% of monthly total."
                        }
                        Text(
                            text = insightText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailySpendingBarChartCard(
    dailySpends: List<DailySpend>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedDay by remember { mutableStateOf<DailySpend?>(null) }
    val maxExpense = dailySpends.maxOfOrNull { it.totalExpense } ?: 1.0
    val effectiveMax = if (maxExpense <= 0.0) 1000.0 else maxExpense
    val activeDays = dailySpends.filter { it.totalExpense > 0 }
    val avgDailySpend = if (activeDays.isNotEmpty()) activeDays.sumOf { it.totalExpense } / activeDays.size else 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chart_daily_spend_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2563EB).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Spending Activity",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Active avg: ${CurrencyFormatter.formatCompact(avgDailySpend)}/day",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Peak: ${CurrencyFormatter.formatCompact(maxExpense)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Interactive Bar Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dailySpends.forEach { day ->
                    val ratio = (day.totalExpense / effectiveMax).toFloat().coerceIn(0.04f, 1f)
                    val barHeight = (ratio * 96).dp
                    val isPeak = day.totalExpense == maxExpense && maxExpense > 0
                    val isSelected = selectedDay == day

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .width(26.dp)
                            .clickable {
                                selectedDay = if (isSelected) null else day
                            }
                    ) {
                        if (day.totalExpense > 0) {
                            Text(
                                text = CurrencyFormatter.formatCompact(day.totalExpense).replace(" ", ""),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPeak) ExpenseRed else if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        Box(
                            modifier = Modifier
                                .width(if (isSelected) 15.dp else 11.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (isPeak) Brush.verticalGradient(listOf(ExpenseRed, Color(0xFFB91C1C)))
                                    else if (isSelected) Brush.verticalGradient(listOf(Color(0xFF38BDF8), EmeraldPrimary))
                                    else if (day.totalExpense > 0) Brush.verticalGradient(listOf(EmeraldAccent, EmeraldPrimary))
                                    else Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${day.dayOfMonth}",
                            fontSize = 9.5.sp,
                            fontWeight = if (isPeak || isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selected Day Inspection Tooltip
            AnimatedVisibility(
                visible = selectedDay != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedDay?.let { day ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Day ${day.dayOfMonth} Summary:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Spent ${CurrencyFormatter.formatCurrency(day.totalExpense)}" + if (day.transactionCount > 0) " (${day.transactionCount} records)" else "",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (day.totalExpense > 0) ExpenseRed else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI Financial Trends & Predictive Budget Burn Rate Card
 */
@Composable
fun AiFinancialTrendsCard(
    summary: MonthFinancialSummary?,
    categoryBreakdown: List<CategorySpend>,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    val totalIncome = summary.totalIncome
    val totalExpense = summary.totalExpense
    val budgetLimit = summary.budgetLimit
    val totalFlow = totalIncome + totalExpense
    val savingsRate = if (totalIncome > 0) (((totalIncome - totalExpense) / totalIncome) * 100).coerceAtLeast(0.0) else 0.0

    val cal = Calendar.getInstance()
    val currentDay = cal.get(Calendar.DAY_OF_MONTH)
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthProgress = (currentDay.toFloat() / totalDays.toFloat()) * 100f

    val budgetUsedPercent = if (budgetLimit > 0) ((totalExpense / budgetLimit) * 100).toFloat() else 0f
    val isPacingHealthy = budgetUsedPercent <= monthProgress

    // Compute Financial Health Score (0 - 100)
    val healthScore = remember(totalIncome, totalExpense, budgetLimit, budgetUsedPercent, monthProgress) {
        var score = 70
        if (totalIncome > 0) {
            if (savingsRate > 30) score += 15
            else if (savingsRate > 15) score += 10
            else if (savingsRate < 0) score -= 25
        }
        if (budgetLimit > 0) {
            if (isPacingHealthy) score += 15
            else score -= ((budgetUsedPercent - monthProgress) * 0.8f).toInt()
        }
        score.coerceIn(15, 99)
    }

    val healthLabel = when {
        healthScore >= 80 -> "Excellent Health"
        healthScore >= 65 -> "Stable & Balanced"
        healthScore >= 50 -> "Caution: High Burn"
        else -> "Overspending Alert"
    }

    val healthColor = when {
        healthScore >= 80 -> EmeraldPrimary
        healthScore >= 65 -> Color(0xFF0284C7)
        healthScore >= 50 -> GoldAmber
        else -> ExpenseRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chart_ai_trends_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(healthColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = healthColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "AI Financial Pacing",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Day $currentDay of $totalDays • Month elapsed ${monthProgress.toInt()}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = healthColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$healthScore/100",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = healthColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Score",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = healthColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Health Status Banner
            Surface(
                color = healthColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(healthColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = healthLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = healthColor
                        )
                    }
                    Text(
                        text = if (isPacingHealthy) "Pacing: On Track" else "Pacing: High Velocity",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cash Flow Ratio Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cash Flow Ratio",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Savings: ${String.format(Locale.US, "%.1f", savingsRate)}%",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    val expenseRatio = if (totalFlow > 0) (totalExpense / totalFlow).toFloat().coerceIn(0f, 1f) else 0f
                    val incomeRatio = if (totalFlow > 0) (1f - expenseRatio) else 1f

                    if (totalFlow > 0) {
                        Box(
                            modifier = Modifier
                                .weight(expenseRatio.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(ExpenseRed)
                        )
                        Box(
                            modifier = Modifier
                                .weight(incomeRatio.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(EmeraldPrimary)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFE2E8F0))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ExpenseRed))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Spend: ${CurrencyFormatter.formatCompact(totalExpense)}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(EmeraldPrimary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Income: ${CurrencyFormatter.formatCompact(totalIncome)}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Budget Burn Rate Linear Gauge
            if (budgetLimit > 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Budget Cap Utilization",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${budgetUsedPercent.toInt()}% of ${CurrencyFormatter.formatCompact(budgetLimit)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (budgetUsedPercent > 100) ExpenseRed else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (budgetUsedPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (budgetUsedPercent > 100) ExpenseRed else if (budgetUsedPercent > 80) GoldAmber else EmeraldPrimary,
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Forecast Summary
            val projectedSpend = if (currentDay > 0) (totalExpense / currentDay) * totalDays else totalExpense
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Projected month-end expenditure: ${CurrencyFormatter.formatCurrency(projectedSpend)}. ${if (budgetLimit > 0 && projectedSpend > budgetLimit) "Warning: On track to exceed limit by ${CurrencyFormatter.formatCurrency(projectedSpend - budgetLimit)}." else "Great job staying within planned budget!"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
