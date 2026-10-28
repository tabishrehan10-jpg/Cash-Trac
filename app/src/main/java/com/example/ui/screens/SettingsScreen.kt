package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.ui.UiState
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.CurrencyItem
import com.example.util.DateUtils
import com.example.util.LocalizationManager

@Composable
fun SettingsScreen(
    uiState: UiState,
    onUpdateStartingBalance: (Double) -> Unit,
    onUpdateBudgetLimit: (Double) -> Unit,
    onToggleAutoRollover: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onLockAppNow: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSignOutGoogle: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showBalanceDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var tempAmountText by remember { mutableStateOf("") }

    val currentBudget = uiState.budgetConfig
    val activeLang = LocalizationManager.supportedLanguages.firstOrNull { it.code == uiState.languageCode }
        ?: LocalizationManager.supportedLanguages[0]
    val activeCurr = CurrencyFormatter.supportedCurrencies.firstOrNull { it.code == uiState.currencyCode }
        ?: CurrencyFormatter.supportedCurrencies[0]

    fun str(key: String): String = LocalizationManager.getString(key, uiState.languageCode)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 0: Google Account & Cloud Storage
        item {
            Text(
                text = "GOOGLE ACCOUNT & CLOUD STORAGE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.userEmail ?: "Connected with Google",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cross-device sync: Active",
                                fontSize = 11.5.sp,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "15-Day Inactivity Policy",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.sessionDaysRemaining} days remaining before session auto-expires",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Cloud", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onSignOutGoogle,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // Section 1: Appearance & Theme
        item {
            Text(
                text = str("theme_setting").uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose App Theme",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select light, dark, or automatically detect from your device settings.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionChip(
                            title = "Device System",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = uiState.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )

                        ThemeOptionChip(
                            title = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = uiState.themeMode == ThemeMode.LIGHT,
                            onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )

                        ThemeOptionChip(
                            title = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = uiState.themeMode == ThemeMode.DARK,
                            onClick = { onSelectThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 2: Language & Currency Localization
        item {
            Text(
                text = "LOCALIZATION & CURRENCY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Language Row
                    SettingItemRow(
                        title = str("language_setting"),
                        subtitle = "${activeLang.name} (${activeLang.nativeName})",
                        icon = Icons.Default.Translate,
                        accentColor = Color(0xFF2563EB),
                        trailing = {
                            FilledTonalButton(
                                onClick = { showLanguageDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("btn_change_language")
                            ) {
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Currency Row
                    SettingItemRow(
                        title = str("currency_setting"),
                        subtitle = "${activeCurr.name} (${activeCurr.symbol} ${activeCurr.code})",
                        icon = Icons.Default.Paid,
                        accentColor = EmeraldPrimary,
                        trailing = {
                            FilledTonalButton(
                                onClick = { showCurrencyDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("btn_change_currency")
                            ) {
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                    )
                }
            }
        }

        // Section 3: Fiscal Month & Rollover Rules
        item {
            Text(
                text = str("month_start_balance").uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month Starting Balance Row
                    SettingItemRow(
                        title = str("month_start_balance"),
                        subtitle = "Current: ${CurrencyFormatter.formatCurrency(currentBudget?.startingBalance ?: 0.0)} for ${DateUtils.formatDisplayMonth(uiState.selectedMonthKey)}",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = EmeraldPrimary,
                        trailing = {
                            FilledTonalButton(
                                onClick = {
                                    tempAmountText = (currentBudget?.startingBalance ?: 0.0).toString()
                                    showBalanceDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("btn_edit_start_balance")
                            ) {
                                Text("Edit", fontSize = 12.sp)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Auto-Rollover Switch
                    SettingItemRow(
                        title = str("auto_rollover"),
                        subtitle = "${str("auto_rollover_desc")} (+${CurrencyFormatter.formatCurrency(currentBudget?.rolledOverFromPrevious ?: 0.0)} rolled over)",
                        icon = Icons.Default.Autorenew,
                        accentColor = GoldAmber,
                        trailing = {
                            Switch(
                                checked = currentBudget?.isAutoRollOverEnabled ?: true,
                                onCheckedChange = onToggleAutoRollover,
                                modifier = Modifier.testTag("switch_auto_rollover")
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Monthly Budget Limit
                    SettingItemRow(
                        title = str("budget_cap"),
                        subtitle = "Target: ${CurrencyFormatter.formatCurrency(currentBudget?.monthlyBudgetLimit ?: 0.0)}",
                        icon = Icons.Default.Savings,
                        accentColor = Color(0xFF2563EB),
                        trailing = {
                            FilledTonalButton(
                                onClick = {
                                    tempAmountText = (currentBudget?.monthlyBudgetLimit ?: 0.0).toString()
                                    showBudgetDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("btn_edit_budget_cap")
                            ) {
                                Text("Edit", fontSize = 12.sp)
                            }
                        }
                    )
                }
            }
        }

        // Section 4: Security & Biometric Login
        item {
            Text(
                text = "SECURITY & BIOMETRIC LOGIN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingItemRow(
                        title = str("biometric_lock"),
                        subtitle = str("biometric_desc"),
                        icon = Icons.Default.Fingerprint,
                        accentColor = EmeraldAccent,
                        trailing = {
                            Switch(
                                checked = uiState.isBiometricEnabled,
                                onCheckedChange = onToggleBiometric,
                                modifier = Modifier.testTag("switch_biometric_lock")
                            )
                        }
                    )

                    if (uiState.isBiometricEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        SettingItemRow(
                            title = str("lock_now"),
                            subtitle = "Instantly secure the app session",
                            icon = Icons.Default.Lock,
                            accentColor = ExpenseRed,
                            trailing = {
                                OutlinedButton(
                                    onClick = onLockAppNow,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_lock_app_now")
                                ) {
                                    Text("Lock", fontSize = 12.sp)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Language Selector Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LocalizationManager.supportedLanguages.forEach { lang ->
                        val isSelected = lang.code == uiState.languageCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectLanguage(lang.code)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = lang.nativeName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.name,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Currency Selector Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(CurrencyFormatter.supportedCurrencies.size) { index ->
                        val curr = CurrencyFormatter.supportedCurrencies[index]
                        val isSelected = curr.code == uiState.currencyCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectCurrency(curr.code)
                                    showCurrencyDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = curr.symbol.trim(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = curr.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = curr.code,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Starting Balance Dialog
    if (showBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showBalanceDialog = false },
            title = { Text("Set Month Starting Balance") },
            text = {
                Column {
                    Text(
                        text = "Enter opening balance available for ${DateUtils.formatDisplayMonth(uiState.selectedMonthKey)}:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempAmountText,
                        onValueChange = { tempAmountText = it },
                        prefix = { Text("${CurrencyFormatter.activeCurrency.symbol} ") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = tempAmountText.toDoubleOrNull() ?: 0.0
                        onUpdateStartingBalance(amount)
                        showBalanceDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBalanceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Monthly Budget Cap Dialog
    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Set Monthly Spending Cap") },
            text = {
                Column {
                    Text(
                        text = "Set your desired monthly expense limit:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempAmountText,
                        onValueChange = { tempAmountText = it },
                        prefix = { Text("${CurrencyFormatter.activeCurrency.symbol} ") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = tempAmountText.toDoubleOrNull() ?: 0.0
                        onUpdateBudgetLimit(amount)
                        showBudgetDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ThemeOptionChip(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SettingItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        trailing()
    }
}
