package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExpenseTransaction
import com.example.ui.components.AddEditTransactionSheet
import com.example.ui.components.BiometricLockOverlay
import com.example.ui.components.BulkUploadConfirmationScreen
import com.example.ui.components.ReceiptScanSheet
import com.example.ui.components.ReceiptTextExtractionSheet
import com.example.ui.components.StatementReviewSheet
import com.example.ui.components.VoiceEntryDialog
import com.example.ui.screens.*
import com.example.util.LocalizationManager

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddEditSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<ExpenseTransaction?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }

    val csvFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.openCsvFileForBulkUpload(context, uri)
        }
    }

    // Handle back button: if not on dashboard, return to dashboard
    BackHandler(enabled = uiState.activeTab != 0) {
        viewModel.selectTab(0)
    }

    // Show notifications in snackbar
    LaunchedEffect(uiState.userNotification) {
        uiState.userNotification?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotification()
        }
    }

    val navItems = listOf(
        NavTabItem(LocalizationManager.getString("nav_dashboard", uiState.languageCode), Icons.Default.Dashboard, "nav_dashboard"),
        NavTabItem(LocalizationManager.getString("nav_ledger", uiState.languageCode), Icons.AutoMirrored.Filled.ReceiptLong, "nav_transactions"),
        NavTabItem(LocalizationManager.getString("nav_smart_log", uiState.languageCode), Icons.Default.ElectricBolt, "nav_smart_capture"),
        NavTabItem(LocalizationManager.getString("nav_exports", uiState.languageCode), Icons.Default.CloudSync, "nav_export_cloud"),
        NavTabItem(LocalizationManager.getString("nav_settings", uiState.languageCode), Icons.Default.Tune, "nav_settings")
    )

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(androidx.compose.ui.graphics.Color(0xFF152036)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground_art),
                                    contentDescription = "Cash Trace Logo",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = LocalizationManager.getString("app_title", uiState.languageCode),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        if (uiState.isBiometricEnabled) {
                            IconButton(onClick = { viewModel.lockApp() }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock App",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!WindowInsets.isImeVisible) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = uiState.activeTab == index,
                                onClick = { viewModel.selectTab(index) },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title, fontSize = 10.5.sp) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                when (uiState.activeTab) {
                    0 -> DashboardScreen(
                        uiState = uiState,
                        onSelectMonth = { viewModel.selectMonth(it) },
                        onAdjustBalance = { viewModel.selectTab(4) },
                        onAddTransaction = {
                            editingTransaction = null
                            showAddEditSheet = true
                        },
                        onVoiceEntry = { showVoiceDialog = true },
                        onScanReceipt = { showReceiptSheet = true },
                        onOpenChat = { viewModel.selectTab(2) },
                        onEditTransaction = { tx ->
                            editingTransaction = tx
                            showAddEditSheet = true
                        },
                        onDeleteTransaction = { tx ->
                            viewModel.deleteTransaction(tx)
                        },
                        onViewAllTransactions = { viewModel.selectTab(1) },
                        onOpenReceiptTextExtractor = { viewModel.openReceiptTextExtractor() }
                    )

                    1 -> TransactionsScreen(
                        uiState = uiState,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onFilterTypeChange = { viewModel.updateFilterType(it) },
                        onFilterCategoryChange = { viewModel.updateFilterCategory(it) },
                        onAddTransaction = {
                            editingTransaction = null
                            showAddEditSheet = true
                        },
                        onEditTransaction = { tx ->
                            editingTransaction = tx
                            showAddEditSheet = true
                        },
                        onDeleteTransaction = { tx ->
                            viewModel.deleteTransaction(tx)
                        },
                        onImportCsv = {
                            csvFilePickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                        }
                    )

                    2 -> SmartCaptureScreen(
                        uiState = uiState,
                        onSendMessage = { text, uri -> viewModel.sendChatMessage(text, uri) },
                        onConfirmExpense = { id, parsed ->
                            viewModel.confirmChatMessageExpense(id, parsed)
                        },
                        onConfirmAllTransactions = { id, items ->
                            viewModel.confirmAllChatTransactions(id, items)
                        },
                        onOpenVoiceDialog = { showVoiceDialog = true },
                        onOpenReceiptScanner = { showReceiptSheet = true },
                        onUploadStatement = { uri ->
                            viewModel.parseUploadedStatement(context, uri)
                        },
                        onOpenReceiptTextExtractor = {
                            viewModel.openReceiptTextExtractor()
                        }
                    )

                    3 -> ExportCloudScreen(
                        uiState = uiState,
                        onExportReport = { format ->
                            viewModel.exportReport(context, format)
                        },
                        onCreateBackup = { passphrase ->
                            viewModel.createEncryptedCloudBackup(context, passphrase)
                        },
                        onRestoreBackup = { uri, passphrase ->
                            viewModel.restoreEncryptedBackup(context, uri, passphrase)
                        },
                        onGoogleSignInSuccess = {
                            viewModel.onGoogleSignInSuccess()
                        },
                        onGoogleSignOut = {
                            viewModel.signOutGoogle(context)
                        },
                        onSyncToFirestore = {
                            viewModel.syncToFirestore()
                        },
                        onRestoreFromFirestore = {
                            viewModel.syncFromFirestore()
                        }
                    )

                    4 -> SettingsScreen(
                        uiState = uiState,
                        onUpdateStartingBalance = { viewModel.updateStartingBalance(it) },
                        onUpdateBudgetLimit = { viewModel.updateBudgetLimit(it) },
                        onToggleAutoRollover = { viewModel.toggleAutoRollOver(it) },
                        onToggleBiometric = { viewModel.setBiometricLockEnabled(it) },
                        onLockAppNow = { viewModel.lockApp() },
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onSelectLanguage = { viewModel.setLanguage(it) },
                        onSelectCurrency = { viewModel.setCurrency(it) },
                        onSignOutGoogle = { viewModel.signOutGoogle(context) },
                        onSyncNow = { viewModel.syncToFirestore() }
                    )
                }
            }
        }

        // Modals & Dialogs
        if (showAddEditSheet) {
            AddEditTransactionSheet(
                existingTransaction = editingTransaction,
                defaultMonthKey = uiState.selectedMonthKey,
                onDismiss = {
                    showAddEditSheet = false
                    editingTransaction = null
                },
                onSave = { title, amount, type, category, mode, note, timestamp ->
                    val tx = editingTransaction
                    if (tx == null) {
                        viewModel.addTransaction(
                            title = title,
                            amount = amount,
                            type = type,
                            category = category,
                            paymentMode = mode,
                            note = note,
                            timestamp = timestamp
                        )
                    } else {
                        viewModel.updateTransaction(
                            tx.copy(
                                title = title,
                                amount = amount,
                                type = type,
                                category = category,
                                paymentMode = mode,
                                note = note,
                                date = timestamp
                            )
                        )
                    }
                    showAddEditSheet = false
                    editingTransaction = null
                }
            )
        }

        if (showVoiceDialog) {
            VoiceEntryDialog(
                pendingExpense = uiState.pendingVoiceExpense,
                onTranscription = { viewModel.onVoiceTranscriptionReceived(it) },
                onConfirmExpense = { parsed ->
                    viewModel.confirmVoiceExpense(parsed)
                    showVoiceDialog = false
                },
                onDismiss = {
                    viewModel.dismissVoiceExpense()
                    showVoiceDialog = false
                }
            )
        }

        if (showReceiptSheet) {
            ReceiptScanSheet(
                pendingResult = uiState.pendingReceiptResult,
                isScanning = uiState.isScanningReceipt,
                onPickImage = { uri -> viewModel.scanReceiptPhoto(context, uri) },
                onConfirm = { result ->
                    viewModel.confirmReceiptExpense(result)
                    showReceiptSheet = false
                },
                onDismiss = {
                    viewModel.dismissReceiptScan()
                    showReceiptSheet = false
                },
                onOpenTextExtractor = {
                    showReceiptSheet = false
                    viewModel.openReceiptTextExtractor()
                }
            )
        }

        if (uiState.showReceiptTextExtractorSheet) {
            ReceiptTextExtractionSheet(
                isExtracting = uiState.isExtractingReceiptDetails,
                extractedDetails = uiState.extractedReceiptDetails,
                errorMessage = uiState.receiptExtractionError,
                onExtract = { text, isReceipt ->
                    viewModel.extractReceiptOrLogText(text, isReceipt)
                },
                onExtractImage = { uri ->
                    viewModel.extractFromWrittenTransactionImage(context, uri)
                },
                onConfirm = { details ->
                    viewModel.confirmExtractedReceiptDetails(details)
                },
                onDismiss = {
                    viewModel.closeReceiptTextExtractor()
                }
            )
        }

        uiState.statementParseResult?.let { result ->
            StatementReviewSheet(
                result = result,
                onConfirmImport = { items ->
                    viewModel.confirmStatementImport(items)
                },
                onDismiss = {
                    viewModel.dismissStatementReview()
                }
            )
        }

        if (uiState.showBulkUploadSheet &&
            uiState.pendingCsvRawData != null &&
            uiState.pendingCsvMapping != null &&
            uiState.pendingCsvParseResult != null
        ) {
            BulkUploadConfirmationScreen(
                rawData = uiState.pendingCsvRawData!!,
                initialMapping = uiState.pendingCsvMapping!!,
                initialParseResult = uiState.pendingCsvParseResult!!,
                onConfirmImport = { items ->
                    viewModel.confirmBulkCsvImport(items)
                },
                onRemapColumns = { newMapping ->
                    viewModel.remapCsvColumns(newMapping)
                },
                onDismiss = {
                    viewModel.dismissBulkCsvUpload()
                }
            )
        }

        // Biometric Lock Screen Overlay
        AnimatedVisibility(
            visible = uiState.isAppLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BiometricLockOverlay(
                onUnlockSuccess = { viewModel.unlockApp() }
            )
        }
    }
}
