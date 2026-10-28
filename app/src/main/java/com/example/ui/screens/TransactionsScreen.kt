package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Categories
import com.example.data.ExpenseTransaction
import com.example.data.TransactionType
import com.example.ui.FilterType
import com.example.ui.UiState
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

@Composable
fun TransactionsScreen(
    uiState: UiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterTypeChange: (FilterType) -> Unit,
    onFilterCategoryChange: (String?) -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (ExpenseTransaction) -> Unit,
    onDeleteTransaction: (ExpenseTransaction) -> Unit,
    onImportCsv: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Filter transactions in memory based on search query and filters
    val filteredList = remember(uiState.transactions, uiState.searchQuery, uiState.filterType, uiState.filterCategory) {
        uiState.transactions.filter { tx ->
            val matchesQuery = if (uiState.searchQuery.isBlank()) true else {
                tx.title.contains(uiState.searchQuery, ignoreCase = true) ||
                tx.category.contains(uiState.searchQuery, ignoreCase = true) ||
                tx.note.contains(uiState.searchQuery, ignoreCase = true) ||
                tx.paymentMode.displayName.contains(uiState.searchQuery, ignoreCase = true)
            }

            val matchesType = when (uiState.filterType) {
                FilterType.ALL -> true
                FilterType.EXPENSE_ONLY -> tx.type == TransactionType.EXPENSE
                FilterType.INCOME_ONLY -> tx.type == TransactionType.INCOME
            }

            val matchesCategory = if (uiState.filterCategory == null) true else {
                tx.category.equals(uiState.filterCategory, ignoreCase = true)
            }

            matchesQuery && matchesType && matchesCategory
        }
    }

    val totalFiltered = filteredList.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = EmeraldPrimary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = 70.dp)
                    .testTag("fab_add_transaction")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar & Import Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search title, category, notes, UPI...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tx_search_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = onImportCsv,
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = EmeraldPrimary.copy(alpha = 0.15f),
                        contentColor = EmeraldPrimary
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("btn_import_csv")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import CSV", modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips (All, Debits, Credits)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.filterType == FilterType.ALL,
                    onClick = { onFilterTypeChange(FilterType.ALL) },
                    label = { Text("All (${uiState.transactions.size})") }
                )

                FilterChip(
                    selected = uiState.filterType == FilterType.EXPENSE_ONLY,
                    onClick = { onFilterTypeChange(FilterType.EXPENSE_ONLY) },
                    label = { Text("Debits (Expenses)") },
                    leadingIcon = {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(14.dp))
                    }
                )

                FilterChip(
                    selected = uiState.filterType == FilterType.INCOME_ONLY,
                    onClick = { onFilterTypeChange(FilterType.INCOME_ONLY) },
                    label = { Text("Credits (Income)") },
                    leadingIcon = {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(14.dp))
                    }
                )

                if (uiState.filterCategory != null) {
                    AssistChip(
                        onClick = { onFilterCategoryChange(null) },
                        label = { Text("${uiState.filterCategory} ✕") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Total summary bar of filtered items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredList.size} items found",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Net: ${if (totalFiltered >= 0) "+" else ""}${CurrencyFormatter.formatInr(totalFiltered)}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (totalFiltered >= 0) IncomeGreen else ExpenseRed
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // List of Transactions
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching transactions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Try clearing filters or add a new transaction",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            onClick = { onEditTransaction(tx) },
                            onDelete = { onDeleteTransaction(tx) }
                        )
                    }
                }
            }
        }
    }
}
