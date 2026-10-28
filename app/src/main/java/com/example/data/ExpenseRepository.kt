package com.example.data

import com.example.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.Calendar

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao
) {
    fun getTransactionsForMonth(monthKey: String): Flow<List<ExpenseTransaction>> {
        val (start, end) = DateUtils.getMonthStartAndEndTime(monthKey)
        return expenseDao.getTransactionsBetweenFlow(start, end)
    }

    fun getAllTransactionsFlow(): Flow<List<ExpenseTransaction>> = expenseDao.getAllTransactionsFlow()

    suspend fun getTransactionsForMonthList(monthKey: String): List<ExpenseTransaction> {
        val (start, end) = DateUtils.getMonthStartAndEndTime(monthKey)
        return expenseDao.getTransactionsBetween(start, end)
    }

    suspend fun getAllBudgets(): List<MonthBudgetConfig> = budgetDao.getAllBudgets()

    suspend fun insertAllBudgets(budgets: List<MonthBudgetConfig>) = budgetDao.insertAllBudgets(budgets)

    suspend fun insertAllTransactions(transactions: List<ExpenseTransaction>): List<ExpenseTransaction> {
        val ids = expenseDao.insertAll(transactions)
        val withIds = transactions.mapIndexed { index, tx ->
            val assignedId = ids.getOrNull(index) ?: tx.id
            if (tx.id == 0L && assignedId > 0L) tx.copy(id = assignedId) else tx
        }
        transactions.firstOrNull()?.let { refreshNextMonthRollover(it.timestamp) }
        return withIds
    }

    suspend fun clearAllData() {
        expenseDao.clearAll()
        budgetDao.clearAllBudgets()
    }

    suspend fun getBudgetConfig(monthKey: String): MonthBudgetConfig {
        var config = budgetDao.getBudget(monthKey)
        if (config == null) {
            // Check previous month for rollover
            val prevKey = DateUtils.getPreviousMonthKey(monthKey)
            val rolledOver = calculateMonthEndingLeftover(prevKey)
            config = MonthBudgetConfig(
                monthKey = monthKey,
                startingBalance = 0.0,
                monthlyBudgetLimit = 40000.0,
                rolledOverFromPrevious = rolledOver,
                isAutoRollOverEnabled = true
            )
            budgetDao.saveBudget(config)
        } else if (config.isAutoRollOverEnabled) {
            val prevKey = DateUtils.getPreviousMonthKey(monthKey)
            val rolledOver = calculateMonthEndingLeftover(prevKey)
            if (config.rolledOverFromPrevious != rolledOver) {
                config = config.copy(rolledOverFromPrevious = rolledOver)
                budgetDao.saveBudget(config)
            }
        }
        return config
    }

    fun getBudgetConfigFlow(monthKey: String): Flow<MonthBudgetConfig?> {
        return budgetDao.getBudgetFlow(monthKey)
    }

    suspend fun calculateMonthEndingLeftover(monthKey: String): Double {
        val prevBudget = budgetDao.getBudget(monthKey) ?: return 0.0
        val (start, end) = DateUtils.getMonthStartAndEndTime(monthKey)
        val transactions = expenseDao.getTransactionsBetween(start, end)
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val effectiveStarting = prevBudget.startingBalance + prevBudget.rolledOverFromPrevious
        val leftover = effectiveStarting + income - expense
        return leftover
    }

    suspend fun saveBudgetConfig(config: MonthBudgetConfig) {
        budgetDao.saveBudget(config)
    }

    suspend fun updateStartingBalance(monthKey: String, startingBalance: Double) {
        val current = getBudgetConfig(monthKey)
        val updated = current.copy(startingBalance = startingBalance)
        budgetDao.saveBudget(updated)
    }

    suspend fun updateBudgetLimit(monthKey: String, budgetLimit: Double) {
        val current = getBudgetConfig(monthKey)
        val updated = current.copy(monthlyBudgetLimit = budgetLimit)
        budgetDao.saveBudget(updated)
    }

    suspend fun toggleAutoRollOver(monthKey: String, enabled: Boolean) {
        val current = getBudgetConfig(monthKey)
        val updated = current.copy(isAutoRollOverEnabled = enabled)
        budgetDao.saveBudget(updated)
    }

    suspend fun insertTransaction(transaction: ExpenseTransaction): Long {
        val id = expenseDao.insertTransaction(transaction)
        refreshNextMonthRollover(transaction.timestamp)
        return id
    }

    suspend fun updateTransaction(transaction: ExpenseTransaction) {
        expenseDao.updateTransaction(transaction)
        refreshNextMonthRollover(transaction.timestamp)
    }

    suspend fun deleteTransaction(transaction: ExpenseTransaction) {
        expenseDao.deleteTransaction(transaction)
        refreshNextMonthRollover(transaction.timestamp)
    }

    private suspend fun refreshNextMonthRollover(timestamp: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        var currentKey = String.format(java.util.Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        for (i in 0 until 12) {
            val nextKey = DateUtils.getNextMonthKey(currentKey)
            val nextBudget = budgetDao.getBudget(nextKey)
            if (nextBudget != null) {
                if (nextBudget.isAutoRollOverEnabled) {
                    val leftover = calculateMonthEndingLeftover(currentKey)
                    budgetDao.saveBudget(nextBudget.copy(rolledOverFromPrevious = leftover))
                }
                currentKey = nextKey
            } else {
                // If next month doesn't exist yet, we don't need to cascade further
                break
            }
        }
    }

    suspend fun seedInitialDataIfEmpty() {
        val all = expenseDao.getAllTransactions()
        if (all.isEmpty()) {
            val currentMonth = DateUtils.getCurrentMonthKey()
            val prevMonth = DateUtils.getPreviousMonthKey(currentMonth)

            // Setup previous month budget & rollover leftover
            budgetDao.saveBudget(
                MonthBudgetConfig(
                    monthKey = prevMonth,
                    startingBalance = 15000.0,
                    monthlyBudgetLimit = 45000.0,
                    rolledOverFromPrevious = 0.0,
                    isAutoRollOverEnabled = true
                )
            )

            // Setup current month
            val starting = 20000.0
            val rolledOver = 12450.0 // Rolled over from previous month
            budgetDao.saveBudget(
                MonthBudgetConfig(
                    monthKey = currentMonth,
                    startingBalance = starting,
                    monthlyBudgetLimit = 50000.0,
                    rolledOverFromPrevious = rolledOver,
                    isAutoRollOverEnabled = true
                )
            )

            val now = System.currentTimeMillis()
            val oneDay = 86400000L

            val seedTransactions = listOf(
                ExpenseTransaction(
                    title = "Monthly Salary Credit",
                    amount = 65000.0,
                    type = TransactionType.INCOME,
                    category = "Salary",
                    paymentMode = PaymentMode.NET_BANKING,
                    timestamp = now - (12 * oneDay),
                    note = "October direct payroll transfer",
                    sourceTag = "MANUAL"
                ),
                ExpenseTransaction(
                    title = "Freelance UI Project",
                    amount = 18500.0,
                    type = TransactionType.INCOME,
                    category = "Business/Freelance",
                    paymentMode = PaymentMode.UPI,
                    timestamp = now - (5 * oneDay),
                    note = "Client milestone payment via GPay",
                    sourceTag = "CHAT"
                ),
                ExpenseTransaction(
                    title = "House Rent & Maintenance",
                    amount = 18000.0,
                    type = TransactionType.EXPENSE,
                    category = "Housing & Rent",
                    paymentMode = PaymentMode.UPI,
                    timestamp = now - (10 * oneDay),
                    note = "Flat 402 rent paid",
                    sourceTag = "MANUAL"
                ),
                ExpenseTransaction(
                    title = "Supermarket Grocery Shopping",
                    amount = 4350.0,
                    type = TransactionType.EXPENSE,
                    category = "Groceries",
                    paymentMode = PaymentMode.CREDIT_CARD,
                    timestamp = now - (7 * oneDay),
                    note = "Nature's Basket essentials & fruits",
                    sourceTag = "PHOTO"
                ),
                ExpenseTransaction(
                    title = "Swiggy Weekend Dinner",
                    amount = 890.0,
                    type = TransactionType.EXPENSE,
                    category = "Food & Dining",
                    paymentMode = PaymentMode.UPI,
                    timestamp = now - (3 * oneDay),
                    note = "Biryani & starter delivery",
                    sourceTag = "VOICE"
                ),
                ExpenseTransaction(
                    title = "Petrol Refill - Indian Oil",
                    amount = 2200.0,
                    type = TransactionType.EXPENSE,
                    category = "Transport & Fuel",
                    paymentMode = PaymentMode.CREDIT_CARD,
                    timestamp = now - (2 * oneDay),
                    note = "Full tank car fuel",
                    sourceTag = "MANUAL"
                ),
                ExpenseTransaction(
                    title = "Electricity & WiFi Bills",
                    amount = 3100.0,
                    type = TransactionType.EXPENSE,
                    category = "Utilities & Bills",
                    paymentMode = PaymentMode.UPI,
                    timestamp = now - (1 * oneDay),
                    note = "Electricity ₹2100 + Airtel Fiber ₹1000",
                    sourceTag = "CHAT"
                ),
                ExpenseTransaction(
                    title = "Pharmacy Medicines",
                    amount = 750.0,
                    type = TransactionType.EXPENSE,
                    category = "Healthcare & Meds",
                    paymentMode = PaymentMode.CASH,
                    timestamp = now - (4 * 3600000L),
                    note = "Apollo Pharmacy prescription",
                    sourceTag = "VOICE"
                )
            )
            expenseDao.insertAll(seedTransactions)
        }
    }
}
