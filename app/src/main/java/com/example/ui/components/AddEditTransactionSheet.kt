package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.DateUtils
import com.example.util.LocalizationManager
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    existingTransaction: ExpenseTransaction? = null,
    defaultMonthKey: String = DateUtils.getCurrentMonthKey(),
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        timestamp: Long
    ) -> Unit
) {
    var type by remember { mutableStateOf(existingTransaction?.type ?: TransactionType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(
            existingTransaction?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var title by remember { mutableStateOf(existingTransaction?.title ?: "") }
    var selectedCategory by remember {
        mutableStateOf(
            existingTransaction?.category ?: if (type == TransactionType.INCOME) "Salary" else "Food & Dining"
        )
    }
    var paymentMode by remember { mutableStateOf(existingTransaction?.paymentMode ?: PaymentMode.UPI) }
    var note by remember { mutableStateOf(existingTransaction?.note ?: "") }

    // Date computation: default to existing tx timestamp, or the viewed month's 15th/current day
    var selectedTimestamp by remember {
        val initialTime = if (existingTransaction != null) {
            existingTransaction.timestamp
        } else {
            val (start, end) = DateUtils.getMonthStartAndEndTime(defaultMonthKey)
            val now = System.currentTimeMillis()
            if (now in start..end) now else start + (14 * 86400000L) // Middle of selected month
        }
        mutableStateOf(initialTime)
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showAiPasteField by remember { mutableStateOf(false) }
    var aiInputText by remember { mutableStateOf("") }
    var isAiExtracting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val categories = if (type == TransactionType.INCOME) Categories.incomeCategories else Categories.expenseCategories

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTransaction == null) "Add Transaction" else "Edit Transaction",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (existingTransaction == null) {
                    AssistChip(
                        onClick = { showAiPasteField = !showAiPasteField },
                        label = { Text("AI Auto-Fill", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = EmeraldPrimary
                            )
                        }
                    )
                }
            }

            if (showAiPasteField && existingTransaction == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Paste Receipt Text or Transaction SMS Log:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = aiInputText,
                            onValueChange = { aiInputText = it },
                            placeholder = { Text("e.g. Swiggy ₹350 on 2025-05-14 or HDFC alert...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (aiInputText.isNotBlank()) {
                                    coroutineScope.launch {
                                        isAiExtracting = true
                                        try {
                                            val details = com.example.util.ReceiptExtractionService.extractAutomatically(aiInputText)
                                            title = details.vendor
                                            amountText = if (details.totalAmount % 1.0 == 0.0) details.totalAmount.toLong().toString() else details.totalAmount.toString()
                                            type = details.type
                                            selectedCategory = details.category
                                            paymentMode = details.paymentMode
                                            selectedTimestamp = details.timestamp
                                            if (details.confidenceNotes.isNotBlank()) {
                                                note = details.confidenceNotes
                                            }
                                            showAiPasteField = false
                                        } catch (_: Exception) {} finally {
                                            isAiExtracting = false
                                        }
                                    }
                                }
                            },
                            enabled = aiInputText.isNotBlank() && !isAiExtracting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            if (isAiExtracting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extracting with Gemini...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Extract & Auto-Fill Form", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transaction Type Toggle (Debit / Credit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                // Expense (Debit) Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (type == TransactionType.EXPENSE) ExpenseRed else Color.Transparent)
                        .clickable {
                            type = TransactionType.EXPENSE
                            selectedCategory = "Food & Dining"
                        }
                        .testTag("tab_expense_debit"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DEBIT (Expense)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (type == TransactionType.EXPENSE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Income (Credit) Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (type == TransactionType.INCOME) IncomeGreen else Color.Transparent)
                        .clickable {
                            type = TransactionType.INCOME
                            selectedCategory = "Salary"
                        }
                        .testTag("tab_income_credit"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CREDIT (Income)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (type == TransactionType.INCOME) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input with active currency sign
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                        amountText = input
                    }
                },
                label = { Text("Amount (${CurrencyFormatter.activeCurrency.name})") },
                prefix = {
                    Text(
                        text = "${CurrencyFormatter.activeCurrency.symbol} ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_tx_amount")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Transaction Date & Month Selector
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerDialog = true }
                    .testTag("select_tx_date_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Transaction Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateUtils.formatShortDate(selectedTimestamp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Change Date",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title / Description
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Description / Merchant / Source") },
                placeholder = { Text("e.g. Swiggy lunch, Monthly rent, Client fee") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_tx_title")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Picker
            Text(
                text = "Select Category",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = cat.name.equals(selectedCategory, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = cat.name }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(cat.color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = cat.color,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Mode
            Text(
                text = "Payment Mode",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMode.values().forEach { mode ->
                    val isSelected = paymentMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { paymentMode = mode },
                        label = { Text(mode.displayName, fontSize = 11.5.sp) },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notes
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes (optional)") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Save Button
            val isValid = amountText.toDoubleOrNull()?.let { it > 0 } == true
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    onSave(
                        title.ifBlank { if (type == TransactionType.INCOME) "Income Credit" else "Expense Debit" },
                        amount,
                        type,
                        selectedCategory,
                        paymentMode,
                        note,
                        selectedTimestamp
                    )
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_tx_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == TransactionType.INCOME) IncomeGreen else EmeraldPrimary
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (existingTransaction == null) "Add Transaction" else "Save Changes",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Calendar Date Picker Dialog
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedTimestamp
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedTimestamp = it
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
