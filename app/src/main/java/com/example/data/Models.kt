package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType {
    EXPENSE, // Debit
    INCOME   // Credit
}

enum class PaymentMode(val displayName: String) {
    UPI("UPI"),
    CASH("Cash"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    NET_BANKING("Net Banking"),
    WALLET("Wallet"),
    OTHER("Other")
}


@Entity(tableName = "month_budgets")
data class MonthBudgetConfig(
    @PrimaryKey
    val monthKey: String, // format "yyyy-MM", e.g., "2026-10"
    val startingBalance: Double = 0.0,
    val monthlyBudgetLimit: Double = 0.0,
    val rolledOverFromPrevious: Double = 0.0,
    val isAutoRollOverEnabled: Boolean = true
)

data class MonthFinancialSummary(
    val monthKey: String,
    val startingBalance: Double,
    val rolledOverAmount: Double,
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double, // startingBalance + rolledOverAmount + totalIncome - totalExpense
    val budgetLimit: Double,
    val budgetRemaining: Double,
    val budgetUsedPercentage: Float
) {
    val monthlyBudgetLimit: Double
        get() = budgetLimit
}

data class CategorySpend(
    val category: String,
    val totalAmount: Double,
    val count: Int,
    val percentage: Float
)

data class DailySpend(
    val dayOfMonth: Int,
    val dateString: String,
    val totalExpense: Double,
    val totalIncome: Double,
    val transactionCount: Int = 0
)
