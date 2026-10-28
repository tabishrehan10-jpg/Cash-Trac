package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Categories
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.util.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementReviewSheet(
    result: StatementParseResult,
    onConfirmImport: (List<StatementItem>) -> Unit,
    onDismiss: () -> Unit
) {
    var itemsList by remember(result) {
        mutableStateOf(result.items.map { it.copy() })
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingNewRow by remember { mutableStateOf(false) }

    val selectedCount = itemsList.count { it.isSelected }
    val selectedExpenseTotal = itemsList.filter { it.isSelected && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val selectedIncomeTotal = itemsList.filter { it.isSelected && it.type == TransactionType.INCOME }.sumOf { it.amount }
    val duplicateCount = itemsList.count { it.isDuplicate }
    val needsReviewCount = itemsList.count { it.needsReview }

    // Dynamic reconciliation against reported total
    val reportedTotal = result.reportedTotalExpenses
    val isBalanced = reportedTotal != null && Math.abs(selectedExpenseTotal - reportedTotal) < 1.0
    val discrepancy = if (reportedTotal != null) Math.abs(selectedExpenseTotal - reportedTotal) else 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("statement_review_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = result.fileFormat,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Statement & Report Verification",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${result.fileName} • Currency: ${result.currency}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Document Financial Reconciliation Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FINANCIAL RECONCILIATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )

                        // Status Badge
                        if (reportedTotal != null) {
                            if (isBalanced) {
                                Surface(
                                    color = IncomeGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reconciled with Document", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                    }
                                }
                            } else {
                                Surface(
                                    color = ExpenseRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Diff: ${CurrencyFormatter.formatInr(discrepancy)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Document Statement", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Starting Balance (if present)
                        if (result.openingBalance != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Starting Balance", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.formatInr(result.openingBalance), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }

                        // Total Debits vs Reported Total
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (reportedTotal != null) "Reported Spending" else "Total Expenses", fontSize = 10.sp, color = ExpenseRed)
                            Text(
                                if (reportedTotal != null) CurrencyFormatter.formatInr(reportedTotal)
                                else "-${CurrencyFormatter.formatInr(selectedExpenseTotal)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))

                        // Calculated Active Debits
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Selected Debits", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "-${CurrencyFormatter.formatInr(selectedExpenseTotal)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (reportedTotal != null && !isBalanced) ExpenseRed else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Closing / Leftover (if present)
                        if (result.closingBalance != null) {
                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Money Left Over", fontSize = 10.sp, color = IncomeGreen)
                                Text(CurrencyFormatter.formatInr(result.closingBalance), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                            }
                        }
                    }
                }
            }

            // Warnings Banner
            if (result.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF3CD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        result.warnings.take(2).forEach { warn ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(warn, fontSize = 11.sp, color = Color(0xFF856404), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bulk Action & Row Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount of ${itemsList.size} selected",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            itemsList = itemsList.map { it.copy(isSelected = true) }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("All", fontSize = 11.sp)
                    }

                    if (duplicateCount > 0) {
                        TextButton(
                            onClick = {
                                itemsList = itemsList.map { it.copy(isSelected = !it.isDuplicate) }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Skip Dups", fontSize = 11.sp, color = EmeraldPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            itemsList = itemsList.map { it.copy(isSelected = false) }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("None", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = { isAddingNewRow = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Row", tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Interactive Transaction Table
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(itemsList, key = { index, item -> "${item.id}_$index" }) { index, item ->
                    val isIncome = item.type == TransactionType.INCOME
                    val catMeta = Categories.getCategoryMeta(item.category, item.type)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            item.isDuplicate -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            item.isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                itemsList = itemsList.toMutableList().also { list ->
                                    list[index] = item.copy(isSelected = !item.isSelected)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { checked ->
                                    itemsList = itemsList.toMutableList().also { list ->
                                        list[index] = item.copy(isSelected = checked)
                                    }
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(catMeta.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catMeta.icon,
                                    contentDescription = null,
                                    tint = catMeta.color,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (item.isDuplicate) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                "Duplicate",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    if (item.needsReview) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFE0B2)
                                        ) {
                                            Text(
                                                "Review",
                                                fontSize = 9.sp,
                                                color = Color(0xFFE65100),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.category} • ${item.paymentMode.displayName}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "• ${DateUtils.formatShortDate(item.timestamp)}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    if (item.sourcePage > 1) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "• p.${item.sourcePage}",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = (if (isIncome) "+" else "-") + CurrencyFormatter.formatInr(item.amount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) IncomeGreen else ExpenseRed
                            )

                            // Quick Edit Button
                            IconButton(
                                onClick = { editingIndex = index },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            }

                            // Delete Row Button
                            IconButton(
                                onClick = {
                                    itemsList = itemsList.toMutableList().also { it.removeAt(index) }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ExpenseRed.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Confirm Import Button
            Button(
                onClick = {
                    val toImport = itemsList.filter { it.isSelected }
                    if (toImport.isNotEmpty()) {
                        onConfirmImport(toImport)
                    }
                },
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_confirm_import_statement")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Import $selectedCount Verified Transactions (${CurrencyFormatter.formatInr(selectedExpenseTotal)})",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Edit Transaction Dialog
    editingIndex?.let { idx ->
        if (idx in itemsList.indices) {
            val currentItem = itemsList[idx]
            TransactionEditDialog(
                initialItem = currentItem,
                onSave = { updated ->
                    itemsList = itemsList.toMutableList().also { it[idx] = updated }
                    editingIndex = null
                },
                onDismiss = { editingIndex = null }
            )
        }
    }

    // Add New Transaction Dialog
    if (isAddingNewRow) {
        val blankItem = StatementItem(
            title = "",
            amount = 0.0,
            type = TransactionType.EXPENSE,
            category = "Other Expense",
            paymentMode = PaymentMode.UPI,
            timestamp = System.currentTimeMillis()
        )
        TransactionEditDialog(
            initialItem = blankItem,
            isNew = true,
            onSave = { newItem ->
                itemsList = itemsList.toMutableList().also { it.add(0, newItem) }
                isAddingNewRow = false
            },
            onDismiss = { isAddingNewRow = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditDialog(
    initialItem: StatementItem,
    isNew: Boolean = false,
    onSave: (StatementItem) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialItem.title) }
    var amountStr by remember { mutableStateOf(if (initialItem.amount > 0.0) initialItem.amount.toString() else "") }
    var selectedType by remember { mutableStateOf(initialItem.type) }
    var selectedCategory by remember { mutableStateOf(initialItem.category) }
    var selectedMode by remember { mutableStateOf(initialItem.paymentMode) }

    val categories = if (selectedType == TransactionType.INCOME) Categories.incomeCategories else Categories.expenseCategories

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (isNew) "Add Missing Transaction" else "Edit Transaction",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Type Toggle (Expense / Income)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedType == TransactionType.EXPENSE) ExpenseRed else Color.Transparent)
                            .clickable { selectedType = TransactionType.EXPENSE }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Expense",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == TransactionType.EXPENSE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedType == TransactionType.INCOME) IncomeGreen else Color.Transparent)
                            .clickable { selectedType = TransactionType.INCOME }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Income",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == TransactionType.INCOME) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Merchant / Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category selector chip
                Text("Category", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name },
                            label = { Text(cat.name, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (title.isNotBlank() && amt > 0.0) {
                                onSave(
                                    initialItem.copy(
                                        title = title.trim(),
                                        amount = amt,
                                        type = selectedType,
                                        category = selectedCategory,
                                        paymentMode = selectedMode,
                                        needsReview = false
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
