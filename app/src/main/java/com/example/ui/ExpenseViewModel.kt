package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.theme.ThemeMode
import com.example.util.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val parsedExpense: ParsedExpense? = null,
    val extractedTransactions: List<StatementItem> = emptyList(),
    val isConfirmed: Boolean = false,
    val isAnalyzing: Boolean = false,
    val attachedImageUri: String? = null
)

enum class MessageSender {
    USER,
    ASSISTANT
}

enum class FilterType {
    ALL,
    EXPENSE_ONLY,
    INCOME_ONLY
}

data class UiState(
    val selectedMonthKey: String = DateUtils.getCurrentMonthKey(),
    val transactions: List<ExpenseTransaction> = emptyList(),
    val budgetConfig: MonthBudgetConfig? = null,
    val summary: MonthFinancialSummary? = null,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val dailySpendList: List<DailySpend> = emptyList(),
    val searchQuery: String = "",
    val filterType: FilterType = FilterType.ALL,
    val filterCategory: String? = null,
    val isBiometricEnabled: Boolean = false,
    val isAppLocked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageCode: String = "en",
    val currencyCode: String = "INR",
    val chatMessages: List<ChatMessage> = emptyList(),
    val pendingVoiceExpense: ParsedExpense? = null,
    val pendingReceiptResult: ReceiptScanResult? = null,
    val isScanningReceipt: Boolean = false,
    val isParsingStatement: Boolean = false,
    val statementParseResult: StatementParseResult? = null,
    val isSyncingFirestore: Boolean = false,
    val isGoogleSignedIn: Boolean = false,
    val isSessionExpired: Boolean = false,
    val sessionDaysRemaining: Int = 15,
    val authErrorMessage: String? = null,
    val userEmail: String? = null,
    val lastSyncTime: Long? = null,
    val userNotification: String? = null,
    val isExtractingReceiptDetails: Boolean = false,
    val extractedReceiptDetails: ExtractedReceiptDetails? = null,
    val receiptExtractionError: String? = null,
    val showReceiptTextExtractorSheet: Boolean = false,
    val pendingCsvRawData: CsvRawData? = null,
    val pendingCsvMapping: CsvColumnMapping? = null,
    val pendingCsvParseResult: CsvParseResult? = null,
    val showBulkUploadSheet: Boolean = false,
    val activeTab: Int = 0 // 0: Dashboard, 1: Transactions, 2: Smart Capture, 3: Export/Cloud, 4: Settings
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val SESSION_INACTIVITY_LIMIT_MS = 15L * 24L * 60L * 60L * 1000L // 15 days in milliseconds
        const val PREF_LAST_ACTIVE_TIME = "last_active_session_time"
    }

    private val repository: ExpenseRepository
    private val firestoreSyncRepository = FirestoreSyncRepository(application)
    private val prefs = application.getSharedPreferences("rupeewise_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(db.expenseDao(), db.budgetDao())

        val isBiometricOn = prefs.getBoolean("biometric_lock_enabled", false)
        val savedThemeName = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(savedThemeName) } catch (e: Exception) { ThemeMode.SYSTEM }
        val savedLang = prefs.getString("language_code", "en") ?: "en"
        val savedCurrency = prefs.getString("currency_code", "INR") ?: "INR"

        LocalizationManager.currentLanguageCode = savedLang
        val activeCurr = CurrencyFormatter.supportedCurrencies.firstOrNull { it.code == savedCurrency }
            ?: CurrencyFormatter.supportedCurrencies[0]
        CurrencyFormatter.activeCurrency = activeCurr

        val lastSync = prefs.getLong("last_firestore_sync", -1L).takeIf { it > 0L }

        _uiState.update {
            it.copy(
                isBiometricEnabled = isBiometricOn,
                isAppLocked = isBiometricOn,
                themeMode = themeMode,
                languageCode = savedLang,
                currencyCode = savedCurrency,
                lastSyncTime = lastSync
            )
        }

        checkSessionAndRefreshActivity()

        // Add welcome message in chat
        initChatWelcome()

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            loadMonthData(_uiState.value.selectedMonthKey)
            if (_uiState.value.isGoogleSignedIn) {
                // Auto-sync in background on launch for cross-device access
                syncFromFirestore(isAuto = true)
            }
        }
    }

    fun checkSessionAndRefreshActivity() {
        val now = System.currentTimeMillis()
        val lastActive = prefs.getLong(PREF_LAST_ACTIVE_TIME, 0L)
        val isSignedIn = firestoreSyncRepository.isUserSignedIn()

        if (isSignedIn && lastActive > 0L && (now - lastActive) > SESSION_INACTIVITY_LIMIT_MS) {
            // Inactive for more than 15 days -> session expires!
            Firebase.auth.signOut()
            prefs.edit().putLong(PREF_LAST_ACTIVE_TIME, 0L).apply()
            _uiState.update {
                it.copy(
                    isGoogleSignedIn = false,
                    userEmail = null,
                    isSessionExpired = true,
                    sessionDaysRemaining = 0,
                    authErrorMessage = "Your session has expired after 15 days of inactivity. Please sign in with Google to resume."
                )
            }
        } else if (isSignedIn) {
            // Session is valid. Update last active timestamp
            prefs.edit().putLong(PREF_LAST_ACTIVE_TIME, now).apply()
            val daysRemaining = if (lastActive > 0L) {
                val elapsed = now - lastActive
                val rem = 15 - (elapsed / (24L * 60L * 60L * 1000L)).toInt()
                rem.coerceIn(1, 15)
            } else 15

            val email = firestoreSyncRepository.getCurrentUserEmail()
            _uiState.update {
                it.copy(
                    isGoogleSignedIn = true,
                    userEmail = email,
                    isSessionExpired = false,
                    sessionDaysRemaining = daysRemaining,
                    authErrorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isGoogleSignedIn = false,
                    userEmail = null
                )
            }
        }
    }

    fun setAuthErrorMessage(message: String?) {
        _uiState.update { it.copy(authErrorMessage = message) }
    }

    private fun initChatWelcome() {
        val welcome = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Welcome to Cash Trace AI! 📊\n\nI can automatically extract and log transactions from anything:\n• Paste bank SMS alerts (e.g. \"INR 1,450 debited for Swiggy via UPI\")\n• Type natural expenses (e.g. \"Petrol 1200 credit card\")\n• Upload or attach statement documents, Google Sheets/CSV, or receipt photos\n• Ask financial questions (e.g. \"How is my budget pacing this month?\")\n\nHow can I help you trace your cash today?"
        )
        _uiState.update { it.copy(chatMessages = listOf(welcome)) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString("language_code", langCode).apply()
        LocalizationManager.currentLanguageCode = langCode
        _uiState.update { it.copy(languageCode = langCode) }
        showNotification("Language updated")
    }

    fun setCurrency(currencyCode: String) {
        prefs.edit().putString("currency_code", currencyCode).apply()
        val curr = CurrencyFormatter.supportedCurrencies.firstOrNull { it.code == currencyCode }
            ?: CurrencyFormatter.supportedCurrencies[0]
        CurrencyFormatter.activeCurrency = curr
        _uiState.update { it.copy(currencyCode = currencyCode) }
        showNotification("Currency changed to ${curr.name} (${curr.symbol})")
    }

    fun selectMonth(monthKey: String) {
        _uiState.update { it.copy(selectedMonthKey = monthKey) }
        loadMonthData(monthKey)
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    fun loadMonthData(monthKey: String) {
        viewModelScope.launch {
            val budget = repository.getBudgetConfig(monthKey)
            repository.getTransactionsForMonth(monthKey).collect { txList ->
                val summary = computeSummary(monthKey, budget, txList)
                val breakdown = computeCategoryBreakdown(txList)
                val dailySpends = computeDailySpends(monthKey, txList)

                _uiState.update {
                    it.copy(
                        transactions = txList,
                        budgetConfig = budget,
                        summary = summary,
                        categoryBreakdown = breakdown,
                        dailySpendList = dailySpends
                    )
                }
            }
        }
    }

    private fun computeSummary(
        monthKey: String,
        config: MonthBudgetConfig,
        transactions: List<ExpenseTransaction>
    ): MonthFinancialSummary {
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val starting = config.startingBalance
        val rolledOver = if (config.isAutoRollOverEnabled) config.rolledOverFromPrevious else 0.0
        val netBalance = starting + rolledOver + totalIncome - totalExpense

        val limit = config.monthlyBudgetLimit
        val remaining = limit - totalExpense
        val percent = if (limit > 0) ((totalExpense / limit) * 100).toFloat() else 0f

        return MonthFinancialSummary(
            monthKey = monthKey,
            startingBalance = starting,
            rolledOverAmount = rolledOver,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netBalance = netBalance,
            budgetLimit = limit,
            budgetRemaining = remaining,
            budgetUsedPercentage = percent.coerceIn(0f, 100f)
        )
    }

    private fun computeCategoryBreakdown(transactions: List<ExpenseTransaction>): List<CategorySpend> {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalExp = expenses.sumOf { it.amount }
        if (totalExp <= 0.0) return emptyList()

        return expenses.groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategorySpend(
                    category = cat,
                    totalAmount = sum,
                    count = list.size,
                    percentage = ((sum / totalExp) * 100).toFloat()
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    private fun computeDailySpends(monthKey: String, transactions: List<ExpenseTransaction>): List<DailySpend> {
        val daysInMonth = DateUtils.getDaysInMonth(monthKey)
        val cal = Calendar.getInstance()
        val dailyMap = mutableMapOf<Int, Triple<Double, Double, Int>>()

        for (tx in transactions) {
            cal.timeInMillis = tx.timestamp
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val current = dailyMap.getOrDefault(day, Triple(0.0, 0.0, 0))
            if (tx.type == TransactionType.EXPENSE) {
                dailyMap[day] = Triple(current.first + tx.amount, current.second, current.third + 1)
            } else {
                dailyMap[day] = Triple(current.first, current.second + tx.amount, current.third + 1)
            }
        }

        val result = mutableListOf<DailySpend>()
        for (d in 1..daysInMonth) {
            val spend = dailyMap[d]
            result.add(
                DailySpend(
                    dayOfMonth = d,
                    dateString = "$d",
                    totalExpense = spend?.first ?: 0.0,
                    totalIncome = spend?.second ?: 0.0,
                    transactionCount = spend?.third ?: 0
                )
            )
        }
        return result
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMode: PaymentMode,
        timestamp: Long = System.currentTimeMillis(),
        note: String = "",
        receiptUri: String? = null,
        sourceTag: String = "MANUAL"
    ) {
        viewModelScope.launch {
            val tx = ExpenseTransaction(
                title = title.ifBlank { if (type == TransactionType.INCOME) "Income" else "Expense" },
                amount = amount,
                type = type,
                category = category,
                paymentMode = paymentMode,
                timestamp = timestamp,
                note = note,
                receiptUri = receiptUri,
                sourceTag = sourceTag
            )
            val insertedId = repository.insertTransaction(tx)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.syncSingleTransaction(tx.copy(id = insertedId))
            }

            // Switch to the month of the added transaction so the user immediately sees it
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val txMonthKey = String.format(java.util.Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

            _uiState.update { it.copy(selectedMonthKey = txMonthKey) }
            loadMonthData(txMonthKey)

            val currSymbol = CurrencyFormatter.activeCurrency.symbol
            showNotification("Transaction of $currSymbol$amount saved for ${DateUtils.formatDisplayMonth(txMonthKey)}!")
        }
    }

    fun updateTransaction(tx: ExpenseTransaction) {
        viewModelScope.launch {
            repository.updateTransaction(tx)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.syncSingleTransaction(tx)
            }
            showNotification("Transaction updated")
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val txMonthKey = String.format(java.util.Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            loadMonthData(txMonthKey)
        }
    }

    fun deleteTransaction(tx: ExpenseTransaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.deleteSingleTransaction(tx.id)
            }
            showNotification("Transaction deleted")
            loadMonthData(_uiState.value.selectedMonthKey)
        }
    }

    fun updateStartingBalance(startingBalance: Double) {
        viewModelScope.launch {
            repository.updateStartingBalance(_uiState.value.selectedMonthKey, startingBalance)
            val updated = repository.getBudgetConfig(_uiState.value.selectedMonthKey)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.syncSingleBudget(updated)
            }
            loadMonthData(_uiState.value.selectedMonthKey)
            showNotification("Starting balance updated to ${CurrencyFormatter.formatCurrency(startingBalance)}")
        }
    }

    fun updateBudgetLimit(budgetLimit: Double) {
        viewModelScope.launch {
            repository.updateBudgetLimit(_uiState.value.selectedMonthKey, budgetLimit)
            val updated = repository.getBudgetConfig(_uiState.value.selectedMonthKey)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.syncSingleBudget(updated)
            }
            loadMonthData(_uiState.value.selectedMonthKey)
            showNotification("Monthly budget limit set to ${CurrencyFormatter.formatCurrency(budgetLimit)}")
        }
    }

    fun toggleAutoRollOver(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAutoRollOver(_uiState.value.selectedMonthKey, enabled)
            val updated = repository.getBudgetConfig(_uiState.value.selectedMonthKey)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.syncSingleBudget(updated)
            }
            loadMonthData(_uiState.value.selectedMonthKey)
            showNotification(if (enabled) "Auto-rollover enabled for leftover balance" else "Auto-rollover disabled")
        }
    }

    // Smart Conversational Chat with API-based Gemini AI
    fun sendChatMessage(text: String, attachedImageUri: Uri? = null) {
        if (text.isBlank() && attachedImageUri == null) return
        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = text.ifBlank { "Attached document/receipt image for AI parsing" },
            attachedImageUri = attachedImageUri?.toString()
        )
        val assistantId = java.util.UUID.randomUUID().toString()
        val thinkingMsg = ChatMessage(
            id = assistantId,
            sender = MessageSender.ASSISTANT,
            text = "Cash Trace AI is analyzing...",
            isAnalyzing = true
        )
        val currentMsgs = _uiState.value.chatMessages.toMutableList()
        currentMsgs.add(userMsg)
        currentMsgs.add(thinkingMsg)
        _uiState.update { it.copy(chatMessages = currentMsgs) }

        viewModelScope.launch {
            val summary = _uiState.value.summary
            val ledgerContext = "Fiscal Month: ${_uiState.value.selectedMonthKey}, Total Income: ₹${summary?.totalIncome ?: 0.0}, Total Expenses: ₹${summary?.totalExpense ?: 0.0}, Net Balance: ₹${summary?.netBalance ?: 0.0}, Budget Remaining: ₹${summary?.budgetRemaining ?: 0.0}, Top Spend Category: ${_uiState.value.categoryBreakdown.firstOrNull()?.category ?: "None"}"

            val bitmap = if (attachedImageUri != null) {
                try {
                    val app = getApplication<Application>()
                    app.contentResolver.openInputStream(attachedImageUri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    null
                }
            } else null

            val result = GeminiAiService.processChatMessage(
                userMessage = text,
                ledgerContext = ledgerContext,
                attachedBitmap = bitmap
            )

            val singleParsed = if (result.transactions.size == 1) {
                val tx = result.transactions[0]
                ParsedExpense(
                    title = tx.title,
                    amount = tx.amount,
                    type = tx.type,
                    category = tx.category,
                    paymentMode = tx.paymentMode,
                    confidence = 0.95f,
                    rawText = text,
                    timestamp = tx.timestamp,
                    dateString = ""
                )
            } else null

            val updatedMsgs = _uiState.value.chatMessages.map { msg ->
                if (msg.id == assistantId) {
                    msg.copy(
                        text = result.conversationalSummary,
                        parsedExpense = singleParsed,
                        extractedTransactions = result.transactions,
                        isAnalyzing = false
                    )
                } else msg
            }
            _uiState.update { it.copy(chatMessages = updatedMsgs) }
        }
    }

    fun confirmAllChatTransactions(messageId: String, items: List<StatementItem>) {
        viewModelScope.launch {
            val toImport = StatementParser.toExpenseTransactions(items)
            if (toImport.isEmpty()) return@launch

            repository.insertAllTransactions(toImport)
            if (_uiState.value.isGoogleSignedIn) {
                toImport.forEach { firestoreSyncRepository.syncSingleTransaction(it) }
            }
            loadMonthData(_uiState.value.selectedMonthKey)

            val updated = _uiState.value.chatMessages.map {
                if (it.id == messageId) it.copy(isConfirmed = true) else it
            }
            _uiState.update { it.copy(chatMessages = updated) }
            val totalAmt = toImport.sumOf { it.amount }
            showNotification("Logged ${toImport.size} transactions (${CurrencyFormatter.formatCurrency(totalAmt)}) to Cash Trace ledger!")
        }
    }

    fun confirmChatMessageExpense(messageId: String, parsed: ParsedExpense) {
        // Use parsed timestamp if available, else respect viewed month
        val txTimestamp = if (parsed.timestamp > 0L) {
            parsed.timestamp
        } else if (_uiState.value.selectedMonthKey == DateUtils.getCurrentMonthKey()) {
            System.currentTimeMillis()
        } else {
            val (start, _) = DateUtils.getMonthStartAndEndTime(_uiState.value.selectedMonthKey)
            start + (14 * 86400000L)
        }

        addTransaction(
            title = parsed.title,
            amount = parsed.amount,
            type = parsed.type,
            category = parsed.category,
            paymentMode = parsed.paymentMode,
            timestamp = txTimestamp,
            sourceTag = "CHAT"
        )
        // Mark message confirmed
        val updated = _uiState.value.chatMessages.map {
            if (it.id == messageId) it.copy(isConfirmed = true) else it
        }
        _uiState.update { it.copy(chatMessages = updated) }
    }

    // Voice Input Handling
    fun onVoiceTranscriptionReceived(spokenText: String) {
        val parsed = ExpenseParser.parseInput(spokenText, sourceTag = "VOICE")
        _uiState.update { it.copy(pendingVoiceExpense = parsed) }
    }

    fun confirmVoiceExpense(parsed: ParsedExpense) {
        val txTimestamp = if (_uiState.value.selectedMonthKey == DateUtils.getCurrentMonthKey()) {
            System.currentTimeMillis()
        } else {
            val (start, _) = DateUtils.getMonthStartAndEndTime(_uiState.value.selectedMonthKey)
            start + (14 * 86400000L)
        }

        addTransaction(
            title = parsed.title,
            amount = parsed.amount,
            type = parsed.type,
            category = parsed.category,
            paymentMode = parsed.paymentMode,
            timestamp = txTimestamp,
            sourceTag = "VOICE"
        )
        _uiState.update { it.copy(pendingVoiceExpense = null) }
    }

    fun dismissVoiceExpense() {
        _uiState.update { it.copy(pendingVoiceExpense = null) }
    }

    // Receipt Photo Scanning
    fun scanReceiptPhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningReceipt = true) }
            try {
                val result = ReceiptScanner.scanReceiptImage(context, imageUri)
                _uiState.update { it.copy(pendingReceiptResult = result, isScanningReceipt = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningReceipt = false) }
                showNotification("Could not scan receipt: ${e.localizedMessage}")
            }
        }
    }

    fun confirmReceiptExpense(result: ReceiptScanResult) {
        val txTimestamp = if (_uiState.value.selectedMonthKey == DateUtils.getCurrentMonthKey()) {
            System.currentTimeMillis()
        } else {
            val (start, _) = DateUtils.getMonthStartAndEndTime(_uiState.value.selectedMonthKey)
            start + (14 * 86400000L)
        }

        addTransaction(
            title = result.merchant,
            amount = result.totalAmount,
            type = TransactionType.EXPENSE,
            category = result.category,
            paymentMode = result.paymentMode,
            timestamp = txTimestamp,
            note = "Scanned Receipt: ${result.itemsSummary.joinToString(", ")}",
            receiptUri = result.receiptUri,
            sourceTag = "PHOTO"
        )
        _uiState.update { it.copy(pendingReceiptResult = null) }
    }

    fun dismissReceiptScan() {
        _uiState.update { it.copy(pendingReceiptResult = null) }
    }

    // Gemini API Service for Captured Receipt Text & Transaction Logs
    fun openReceiptTextExtractor(initialText: String? = null) {
        _uiState.update {
            it.copy(
                showReceiptTextExtractorSheet = true,
                receiptExtractionError = null
            )
        }
        if (!initialText.isNullOrBlank()) {
            extractReceiptOrLogText(initialText, isReceipt = true)
        }
    }

    fun closeReceiptTextExtractor() {
        _uiState.update {
            it.copy(
                showReceiptTextExtractorSheet = false,
                isExtractingReceiptDetails = false,
                receiptExtractionError = null
            )
        }
    }

    fun extractReceiptOrLogText(text: String, isReceipt: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExtractingReceiptDetails = true,
                    receiptExtractionError = null
                )
            }
            try {
                val details = if (isReceipt) {
                    ReceiptExtractionService.extractFromReceiptText(text)
                } else {
                    ReceiptExtractionService.extractFromTransactionLog(text)
                }
                _uiState.update {
                    it.copy(
                        isExtractingReceiptDetails = false,
                        extractedReceiptDetails = details,
                        receiptExtractionError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExtractingReceiptDetails = false,
                        receiptExtractionError = "Extraction failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun extractFromWrittenTransactionImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExtractingReceiptDetails = true,
                    receiptExtractionError = null
                )
            }
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                } ?: throw IllegalArgumentException("Could not open image file.")

                val details = ReceiptExtractionService.extractFromWrittenTransactionImage(bitmap)
                _uiState.update {
                    it.copy(
                        isExtractingReceiptDetails = false,
                        extractedReceiptDetails = details,
                        receiptExtractionError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExtractingReceiptDetails = false,
                        receiptExtractionError = "Image extraction failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun confirmExtractedReceiptDetails(details: ExtractedReceiptDetails) {
        val noteText = buildString {
            if (details.confidenceNotes.isNotBlank()) append(details.confidenceNotes)
            if (details.lineItems.isNotEmpty()) {
                if (isNotEmpty()) append(" | Items: ")
                append(details.lineItems.joinToString(", ") { "${it.description} (${CurrencyFormatter.formatCurrency(it.amount)})" })
            }
        }

        addTransaction(
            title = details.vendor,
            amount = details.totalAmount,
            type = details.type,
            category = details.category,
            paymentMode = details.paymentMode,
            timestamp = details.timestamp,
            note = noteText,
            sourceTag = "AI_EXTRACTION"
        )

        _uiState.update {
            it.copy(
                showReceiptTextExtractorSheet = false,
                extractedReceiptDetails = null,
                receiptExtractionError = null
            )
        }
        showNotification("Added ${CurrencyFormatter.formatCurrency(details.totalAmount)} for ${details.vendor} to Cash Trace!")
    }

    // Multi-format Statement Parsing (Google Sheets, CSV, PDF, PNG, JPEG)
    fun parseUploadedStatement(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsingStatement = true) }
            try {
                val existing = _uiState.value.transactions
                val result = StatementParser.parseDocument(context, uri, existingTransactions = existing)
                _uiState.update { it.copy(statementParseResult = result, isParsingStatement = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isParsingStatement = false) }
                showNotification("Could not parse statement: ${e.localizedMessage}")
            }
        }
    }

    fun confirmStatementImport(items: List<StatementItem>) {
        viewModelScope.launch {
            val parseResult = _uiState.value.statementParseResult
            val toImport = StatementParser.toExpenseTransactions(items)
            if (toImport.isEmpty()) {
                _uiState.update { it.copy(statementParseResult = null) }
                return@launch
            }

            // Atomic batch commit to Room
            val insertedTransactions = repository.insertAllTransactions(toImport)

            // Atomic batch commit to Firestore
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.uploadLocalToFirestore(insertedTransactions, emptyList())
            }

            // If starting balance was extracted from document (e.g. September 2026 starting balance ₹138,693),
            // update the month's budget configuration
            val docOpeningBal = parseResult?.openingBalance
            if (docOpeningBal != null && docOpeningBal > 0.0) {
                repository.updateStartingBalance(_uiState.value.selectedMonthKey, docOpeningBal)
            }

            loadMonthData(_uiState.value.selectedMonthKey)
            val totalDebits = toImport.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val totalCredits = toImport.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

            // Add Assistant confirmation to conversational chat
            val reconNote = when (parseResult?.reconciliationStatus) {
                ReconciliationStatus.RECONCILED -> " Reconciled with reported document total (${CurrencyFormatter.formatCurrency(parseResult.reportedTotalExpenses ?: totalDebits)})."
                ReconciliationStatus.DISCREPANCY -> " Note: Discrepancy of ${CurrencyFormatter.formatCurrency(parseResult.discrepancyAmount)} between reported total and transaction sum."
                else -> ""
            }

            val assistantMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Successfully imported ${toImport.size} transactions from statement! Recorded ${CurrencyFormatter.formatCurrency(totalDebits)} in debits and ${CurrencyFormatter.formatCurrency(totalCredits)} in credits into your ledger.$reconNote"
            )
            val currentMsgs = _uiState.value.chatMessages.toMutableList()
            currentMsgs.add(assistantMsg)
            _uiState.update {
                it.copy(
                    statementParseResult = null,
                    chatMessages = currentMsgs
                )
            }
            showNotification("Imported ${toImport.size} transactions into your records!")
        }
    }

    fun dismissStatementReview() {
        _uiState.update { it.copy(statementParseResult = null) }
    }

    // CSV Column Mapping & Bulk Upload
    fun openCsvFileForBulkUpload(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val rawData = CsvTransactionParser.readCsvFile(context, uri)
                val mapping = CsvTransactionParser.inferColumnMapping(rawData.headers)
                val parseResult = CsvTransactionParser.parseWithMapping(
                    rawData = rawData,
                    mapping = mapping,
                    existingTransactions = _uiState.value.transactions
                )
                _uiState.update {
                    it.copy(
                        pendingCsvRawData = rawData,
                        pendingCsvMapping = mapping,
                        pendingCsvParseResult = parseResult,
                        showBulkUploadSheet = true
                    )
                }
            } catch (e: Exception) {
                showNotification("Could not parse CSV: ${e.localizedMessage}")
            }
        }
    }

    fun remapCsvColumns(newMapping: CsvColumnMapping): CsvParseResult {
        val rawData = _uiState.value.pendingCsvRawData ?: return CsvParseResult(
            fileName = "",
            totalRows = 0,
            validRows = 0,
            skippedRows = 0,
            totalDebits = 0.0,
            totalCredits = 0.0,
            items = emptyList()
        )
        val newResult = CsvTransactionParser.parseWithMapping(
            rawData = rawData,
            mapping = newMapping,
            existingTransactions = _uiState.value.transactions
        )
        _uiState.update {
            it.copy(
                pendingCsvMapping = newMapping,
                pendingCsvParseResult = newResult
            )
        }
        return newResult
    }

    fun confirmBulkCsvImport(items: List<StatementItem>) {
        viewModelScope.launch {
            val toImport = StatementParser.toExpenseTransactions(items)
            if (toImport.isEmpty()) {
                dismissBulkCsvUpload()
                return@launch
            }

            val insertedTransactions = repository.insertAllTransactions(toImport)
            if (_uiState.value.isGoogleSignedIn) {
                firestoreSyncRepository.uploadLocalToFirestore(insertedTransactions, emptyList())
            }

            loadMonthData(_uiState.value.selectedMonthKey)
            val totalDebits = toImport.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val totalCredits = toImport.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

            val assistantMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Successfully imported ${toImport.size} transactions from CSV! Recorded ${CurrencyFormatter.formatCurrency(totalDebits)} in debits and ${CurrencyFormatter.formatCurrency(totalCredits)} in credits into your ledger."
            )
            val currentMsgs = _uiState.value.chatMessages.toMutableList()
            currentMsgs.add(assistantMsg)

            _uiState.update {
                it.copy(
                    showBulkUploadSheet = false,
                    pendingCsvRawData = null,
                    pendingCsvMapping = null,
                    pendingCsvParseResult = null,
                    chatMessages = currentMsgs
                )
            }
            showNotification("Bulk imported ${toImport.size} transactions from CSV!")
        }
    }

    fun dismissBulkCsvUpload() {
        _uiState.update {
            it.copy(
                showBulkUploadSheet = false,
                pendingCsvRawData = null,
                pendingCsvMapping = null,
                pendingCsvParseResult = null
            )
        }
    }

    // Search & Filter
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateFilterType(type: FilterType) {
        _uiState.update { it.copy(filterType = type) }
    }

    fun updateFilterCategory(category: String?) {
        _uiState.update { it.copy(filterCategory = category) }
    }

    // Biometric Security
    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_lock_enabled", enabled).apply()
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
        showNotification(if (enabled) "Biometric Lock enabled" else "Biometric Lock disabled")
    }

    fun unlockApp() {
        _uiState.update { it.copy(isAppLocked = false) }
    }

    fun lockApp() {
        if (_uiState.value.isBiometricEnabled) {
            _uiState.update { it.copy(isAppLocked = true) }
        }
    }

    // Encrypted Cloud Backup & Restore
    fun createEncryptedCloudBackup(context: Context, passphrase: String) {
        viewModelScope.launch {
            try {
                val allTx = repository.getAllTransactionsFlow().first()
                val budgets = listOfNotNull(_uiState.value.budgetConfig)
                val payload = CryptoBackupManager.BackupPayload(allTx, budgets)
                val (file, uri) = CryptoBackupManager.createEncryptedBackupFile(context, passphrase, payload)
                CryptoBackupManager.shareToGoogleDrive(context, uri, file.name)
                showNotification("Encrypted backup created! Saving to Google Drive...")
            } catch (e: Exception) {
                showNotification("Backup failed: ${e.localizedMessage}")
            }
        }
    }

    fun restoreEncryptedBackup(context: Context, uri: Uri, passphrase: String) {
        viewModelScope.launch {
            try {
                val payload = CryptoBackupManager.restoreEncryptedBackup(context, uri, passphrase)
                val db = AppDatabase.getDatabase(context)
                db.expenseDao().clearAll()
                db.expenseDao().insertAll(payload.transactions)
                db.budgetDao().clearAllBudgets()
                db.budgetDao().insertAllBudgets(payload.budgets)
                loadMonthData(_uiState.value.selectedMonthKey)
                showNotification("Restored ${payload.transactions.size} transactions successfully!")
            } catch (e: Exception) {
                showNotification("Restore failed: Incorrect password or corrupted file.")
            }
        }
    }

    // Export Reports
    fun exportReport(context: Context, format: ExportManager.ExportFormat) {
        viewModelScope.launch {
            try {
                val monthKey = _uiState.value.selectedMonthKey
                val summary = _uiState.value.summary
                val txList = _uiState.value.transactions
                val (file, uri) = ExportManager.exportTransactions(context, format, monthKey, summary, txList)
                ExportManager.shareExportedFile(context, file, uri, format.mimeType, "${format.label} - $monthKey")
                showNotification("${format.label} ready to share or save!")
            } catch (e: Exception) {
                showNotification("Export error: ${e.localizedMessage}")
            }
        }
    }

    // Firestore Cloud Sync & Google Auth
    fun onGoogleSignInSuccess() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(PREF_LAST_ACTIVE_TIME, now).apply()
        val isBiometricOn = prefs.getBoolean("biometric_lock_enabled", false)
        val email = firestoreSyncRepository.getCurrentUserEmail()
        _uiState.update {
            it.copy(
                isGoogleSignedIn = true,
                isBiometricEnabled = isBiometricOn,
                userEmail = email,
                isSessionExpired = false,
                sessionDaysRemaining = 15,
                authErrorMessage = null,
                isAppLocked = false
            )
        }
        showNotification("Signed in as ${email ?: "Google Account"}! Restoring your cloud data...")
        syncFromFirestore(isAuto = true)
    }

    fun signOutGoogle(context: Context) {
        GoogleAuthHelper.signOut(context, viewModelScope) {
            prefs.edit().putLong(PREF_LAST_ACTIVE_TIME, 0L).apply()
            _uiState.update {
                it.copy(
                    isGoogleSignedIn = false,
                    userEmail = null,
                    isSessionExpired = false,
                    sessionDaysRemaining = 15
                )
            }
            showNotification("Signed out from Google")
        }
    }

    fun syncToFirestore(isAuto: Boolean = false) {
        if (!_uiState.value.isGoogleSignedIn) {
            if (!isAuto) showNotification("Please sign in with Google first to sync with Firestore")
            return
        }
        viewModelScope.launch {
            if (!isAuto) _uiState.update { it.copy(isSyncingFirestore = true) }
            try {
                val allTx = repository.getAllTransactionsFlow().first()
                val budgets = repository.getAllBudgets()
                val result = firestoreSyncRepository.uploadLocalToFirestore(allTx, budgets)
                result.onSuccess { count ->
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("last_firestore_sync", now).apply()
                    _uiState.update {
                        it.copy(
                            isSyncingFirestore = false,
                            lastSyncTime = now
                        )
                    }
                    if (!isAuto) showNotification("Synced $count transactions & budgets to Google Cloud!")
                }.onFailure { err ->
                    _uiState.update { it.copy(isSyncingFirestore = false) }
                    if (!isAuto) showNotification("Sync failed: ${err.localizedMessage ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncingFirestore = false) }
                if (!isAuto) showNotification("Sync failed: ${e.localizedMessage}")
            }
        }
    }

    fun syncFromFirestore(isAuto: Boolean = false) {
        if (!_uiState.value.isGoogleSignedIn) {
            if (!isAuto) showNotification("Please sign in with Google first to restore from Firestore")
            return
        }
        viewModelScope.launch {
            if (!isAuto) _uiState.update { it.copy(isSyncingFirestore = true) }
            try {
                val result = firestoreSyncRepository.downloadFromFirestore()
                result.onSuccess { (remoteTx, remoteBudgets) ->
                    if (remoteTx.isNotEmpty() || remoteBudgets.isNotEmpty()) {
                        repository.insertAllTransactions(remoteTx)
                        repository.insertAllBudgets(remoteBudgets)
                        loadMonthData(_uiState.value.selectedMonthKey)
                    }
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("last_firestore_sync", now).apply()
                    _uiState.update {
                        it.copy(
                            isSyncingFirestore = false,
                            lastSyncTime = now
                        )
                    }
                    if (isAuto) {
                        // After pulling from cloud, upload any local items not yet in cloud
                        syncToFirestore(isAuto = true)
                    } else {
                        showNotification("Restored ${remoteTx.size} transactions & ${remoteBudgets.size} budgets from Google Cloud!")
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(isSyncingFirestore = false) }
                    if (!isAuto) showNotification("Restore failed: ${err.localizedMessage ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncingFirestore = false) }
                if (!isAuto) showNotification("Restore failed: ${e.localizedMessage}")
            }
        }
    }

    fun showNotification(message: String) {
        _uiState.update { it.copy(userNotification = message) }
    }

    fun clearNotification() {
        _uiState.update { it.copy(userNotification = null) }
    }
}
