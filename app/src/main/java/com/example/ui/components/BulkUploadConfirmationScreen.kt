package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkUploadConfirmationScreen(
    rawData: CsvRawData,
    initialMapping: CsvColumnMapping,
    initialParseResult: CsvParseResult,
    onConfirmImport: (List<StatementItem>) -> Unit,
    onRemapColumns: (CsvColumnMapping) -> CsvParseResult,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(1) } // 0 = Mapping, 1 = Verification Table
    var currentMapping by remember(initialMapping) { mutableStateOf(initialMapping) }
    var parseResult by remember(initialParseResult) { mutableStateOf(initialParseResult) }
    var itemsList by remember(parseResult) { mutableStateOf(parseResult.items.map { it.copy() }) }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingNewRow by remember { mutableStateOf(false) }

    val selectedCount = itemsList.count { it.isSelected }
    val selectedExpenseTotal = itemsList.filter { it.isSelected && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val selectedIncomeTotal = itemsList.filter { it.isSelected && it.type == TransactionType.INCOME }.sumOf { it.amount }
    val duplicateCount = itemsList.count { it.isDuplicate }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("bulk_upload_confirmation_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "CSV BULK IMPORT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Bulk Transaction Verification",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${rawData.fileName} • ${rawData.rows.size} rows parsed",
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

            // Step Tab Switcher (0 = Column Mapping, 1 = Verification Table)
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1. Column Mapping", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2. Verify & Import (${itemsList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTab == 0) {
                // TAB 1: Column Mapping Configuration
                ColumnMappingView(
                    headers = rawData.headers,
                    sampleRow = rawData.rows.firstOrNull() ?: emptyList(),
                    currentMapping = currentMapping,
                    onUpdateMapping = { newMapping ->
                        currentMapping = newMapping
                        val newResult = onRemapColumns(newMapping)
                        parseResult = newResult
                        itemsList = newResult.items.map { it.copy() }
                    },
                    onProceedToVerification = { activeTab = 1 }
                )
            } else {
                // TAB 2: Verification & Confirmation Table
                VerificationTableView(
                    parseResult = parseResult,
                    itemsList = itemsList,
                    selectedCount = selectedCount,
                    selectedExpenseTotal = selectedExpenseTotal,
                    selectedIncomeTotal = selectedIncomeTotal,
                    duplicateCount = duplicateCount,
                    onToggleSelect = { index ->
                        itemsList = itemsList.toMutableList().also { list ->
                            list[index] = list[index].copy(isSelected = !list[index].isSelected)
                        }
                    },
                    onSelectAll = {
                        itemsList = itemsList.map { it.copy(isSelected = true) }
                    },
                    onDeselectAll = {
                        itemsList = itemsList.map { it.copy(isSelected = false) }
                    },
                    onSkipDuplicates = {
                        itemsList = itemsList.map { it.copy(isSelected = !it.isDuplicate) }
                    },
                    onEditRow = { index -> editingIndex = index },
                    onDeleteRow = { index ->
                        itemsList = itemsList.toMutableList().also { it.removeAt(index) }
                    },
                    onAddNewRow = { isAddingNewRow = true },
                    onConfirm = {
                        val toImport = itemsList.filter { it.isSelected }
                        if (toImport.isNotEmpty()) {
                            onConfirmImport(toImport)
                        }
                    }
                )
            }
        }
    }

    // Row Edit Dialog
    editingIndex?.let { idx ->
        if (idx in itemsList.indices) {
            val currentItem = itemsList[idx]
            CsvRowEditDialog(
                initialItem = currentItem,
                onSave = { updated ->
                    itemsList = itemsList.toMutableList().also { it[idx] = updated }
                    editingIndex = null
                },
                onDismiss = { editingIndex = null }
            )
        }
    }

    // Add Missing Row Dialog
    if (isAddingNewRow) {
        val blankItem = StatementItem(
            title = "",
            amount = 0.0,
            type = TransactionType.EXPENSE,
            category = "Other Expense",
            paymentMode = PaymentMode.UPI,
            timestamp = System.currentTimeMillis()
        )
        CsvRowEditDialog(
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

@Composable
private fun ColumnMappingView(
    headers: List<String>,
    sampleRow: List<String>,
    currentMapping: CsvColumnMapping,
    onUpdateMapping: (CsvColumnMapping) -> Unit,
    onProceedToVerification: () -> Unit
) {
    val headerOptions = remember(headers) { listOf("None") + headers }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
    ) {
        Text(
            text = "Match CSV Columns to Cash Trace Transaction Model",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Review which column represents Date, Description, and Amounts. Change mappings if needed.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date Column
            item {
                MappingDropdownRow(
                    label = "Transaction Date",
                    options = headerOptions,
                    selectedIndex = currentMapping.dateColumnIndex + 1,
                    onSelected = { chosenIdx ->
                        onUpdateMapping(currentMapping.copy(dateColumnIndex = chosenIdx - 1))
                    }
                )
            }

            // Description / Title Column
            item {
                MappingDropdownRow(
                    label = "Description / Merchant",
                    options = headerOptions,
                    selectedIndex = currentMapping.titleColumnIndex + 1,
                    onSelected = { chosenIdx ->
                        onUpdateMapping(currentMapping.copy(titleColumnIndex = chosenIdx - 1))
                    }
                )
            }

            // Amount Layout Switcher
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Separate Debit & Credit Columns", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Use when withdrawals and deposits are in different columns", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = currentMapping.isSeparateDebitCredit,
                        onCheckedChange = { isSep ->
                            onUpdateMapping(currentMapping.copy(isSeparateDebitCredit = isSep))
                        }
                    )
                }
            }

            if (currentMapping.isSeparateDebitCredit) {
                // Debit Column
                item {
                    MappingDropdownRow(
                        label = "Debit / Expense Column",
                        options = headerOptions,
                        selectedIndex = currentMapping.debitColumnIndex + 1,
                        onSelected = { chosenIdx ->
                            onUpdateMapping(currentMapping.copy(debitColumnIndex = chosenIdx - 1))
                        }
                    )
                }
                // Credit Column
                item {
                    MappingDropdownRow(
                        label = "Credit / Income Column",
                        options = headerOptions,
                        selectedIndex = currentMapping.creditColumnIndex + 1,
                        onSelected = { chosenIdx ->
                            onUpdateMapping(currentMapping.copy(creditColumnIndex = chosenIdx - 1))
                        }
                    )
                }
            } else {
                // Single Amount Column
                item {
                    MappingDropdownRow(
                        label = "Amount Column",
                        options = headerOptions,
                        selectedIndex = currentMapping.amountColumnIndex + 1,
                        onSelected = { chosenIdx ->
                            onUpdateMapping(currentMapping.copy(amountColumnIndex = chosenIdx - 1))
                        }
                    )
                }
            }

            // Category Column
            item {
                MappingDropdownRow(
                    label = "Category (Optional)",
                    options = headerOptions,
                    selectedIndex = currentMapping.categoryColumnIndex + 1,
                    onSelected = { chosenIdx ->
                        onUpdateMapping(currentMapping.copy(categoryColumnIndex = chosenIdx - 1))
                    }
                )
            }

            // Payment Mode Column
            item {
                MappingDropdownRow(
                    label = "Payment Mode (Optional)",
                    options = headerOptions,
                    selectedIndex = currentMapping.paymentModeColumnIndex + 1,
                    onSelected = { chosenIdx ->
                        onUpdateMapping(currentMapping.copy(paymentModeColumnIndex = chosenIdx - 1))
                    }
                )
            }

            // Notes / Remarks Column
            item {
                MappingDropdownRow(
                    label = "Notes / Remarks (Optional)",
                    options = headerOptions,
                    selectedIndex = currentMapping.notesColumnIndex + 1,
                    onSelected = { chosenIdx ->
                        onUpdateMapping(currentMapping.copy(notesColumnIndex = chosenIdx - 1))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onProceedToVerification,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_proceed_to_verification")
        ) {
            Text("Proceed to Verification Table", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MappingDropdownRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = if (selectedIndex in options.indices) options[selectedIndex] else "None"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))

        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = displayValue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (displayValue != "None") EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { idx, opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontSize = 12.sp) },
                        onClick = {
                            onSelected(idx)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationTableView(
    parseResult: CsvParseResult,
    itemsList: List<StatementItem>,
    selectedCount: Int,
    selectedExpenseTotal: Double,
    selectedIncomeTotal: Double,
    duplicateCount: Int,
    onToggleSelect: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSkipDuplicates: () -> Unit,
    onEditRow: (Int) -> Unit,
    onDeleteRow: (Int) -> Unit,
    onAddNewRow: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Financial Metrics Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Parsed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${itemsList.size} rows", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Debits", fontSize = 10.sp, color = ExpenseRed)
                    Text("-${CurrencyFormatter.formatInr(selectedExpenseTotal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                }
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Credits", fontSize = 10.sp, color = IncomeGreen)
                    Text("+${CurrencyFormatter.formatInr(selectedIncomeTotal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bulk Selection Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selectedCount of ${itemsList.size} selected",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onSelectAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("All", fontSize = 11.sp)
                }

                if (duplicateCount > 0) {
                    TextButton(
                        onClick = onSkipDuplicates,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Skip Dups ($duplicateCount)", fontSize = 11.sp, color = EmeraldPrimary)
                    }
                }

                TextButton(
                    onClick = onDeselectAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("None", fontSize = 11.sp)
                }

                IconButton(onClick = onAddNewRow, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add Row", tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Transaction Table
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .fillMaxWidth(),
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
                        .clickable { onToggleSelect(index) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isSelected,
                            onCheckedChange = { onToggleSelect(index) }
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
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = (if (isIncome) "+" else "-") + CurrencyFormatter.formatInr(item.amount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncome) IncomeGreen else ExpenseRed
                        )

                        // Quick Edit
                        IconButton(
                            onClick = { onEditRow(index) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }

                        // Delete
                        IconButton(
                            onClick = { onDeleteRow(index) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ExpenseRed.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Commit Button
        Button(
            onClick = onConfirm,
            enabled = selectedCount > 0,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_confirm_bulk_csv_import")
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Confirm & Import $selectedCount Transactions (${CurrencyFormatter.formatInr(selectedExpenseTotal)})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CsvRowEditDialog(
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
                    text = if (isNew) "Add Transaction" else "Edit CSV Transaction",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                    label = { Text("Title / Description") },
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
                                        paymentMode = selectedMode
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
