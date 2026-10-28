package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryMeta(
    val name: String,
    val type: TransactionType,
    val icon: ImageVector,
    val color: Color
)

object Categories {
    val expenseCategories = listOf(
        CategoryMeta("Food & Dining", TransactionType.EXPENSE, Icons.Default.Restaurant, Color(0xFFFF6D00)),
        CategoryMeta("Groceries", TransactionType.EXPENSE, Icons.Default.ShoppingCart, Color(0xFF00C853)),
        CategoryMeta("Transport & Fuel", TransactionType.EXPENSE, Icons.Default.DirectionsCar, Color(0xFF2979FF)),
        CategoryMeta("Shopping", TransactionType.EXPENSE, Icons.Default.ShoppingBag, Color(0xFFAA00FF)),
        CategoryMeta("Housing & Rent", TransactionType.EXPENSE, Icons.Default.Home, Color(0xFF00B0FF)),
        CategoryMeta("Utilities & Bills", TransactionType.EXPENSE, Icons.Default.Bolt, Color(0xFFFFD600)),
        CategoryMeta("Entertainment", TransactionType.EXPENSE, Icons.Default.Movie, Color(0xFFFF4081)),
        CategoryMeta("Healthcare & Meds", TransactionType.EXPENSE, Icons.Default.LocalHospital, Color(0xFFFF1744)),
        CategoryMeta("Education", TransactionType.EXPENSE, Icons.Default.School, Color(0xFF304FFE)),
        CategoryMeta("Personal Care", TransactionType.EXPENSE, Icons.Default.Spa, Color(0xFF00BFA5)),
        CategoryMeta("Gifts & Donations", TransactionType.EXPENSE, Icons.Default.CardGiftcard, Color(0xFFFF80AB)),
        CategoryMeta("Other Expense", TransactionType.EXPENSE, Icons.Default.MoreHoriz, Color(0xFF78909C))
    )

    val incomeCategories = listOf(
        CategoryMeta("Salary", TransactionType.INCOME, Icons.Default.Payments, Color(0xFF00C853)),
        CategoryMeta("Business/Freelance", TransactionType.INCOME, Icons.Default.Work, Color(0xFF00B0FF)),
        CategoryMeta("Investments", TransactionType.INCOME, Icons.Default.TrendingUp, Color(0xFF651FFF)),
        CategoryMeta("Rental Income", TransactionType.INCOME, Icons.Default.Apartment, Color(0xFFFFAB00)),
        CategoryMeta("Cashback & Rewards", TransactionType.INCOME, Icons.Default.Savings, Color(0xFF00E676)),
        CategoryMeta("Gifts Received", TransactionType.INCOME, Icons.Default.Redeem, Color(0xFFFF4081)),
        CategoryMeta("Refunds", TransactionType.INCOME, Icons.Default.Replay, Color(0xFF26A69A)),
        CategoryMeta("Other Income", TransactionType.INCOME, Icons.Default.AccountBalance, Color(0xFF78909C))
    )

    fun getCategoryMeta(name: String, type: TransactionType): CategoryMeta {
        val list = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
        return list.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: (if (type == TransactionType.INCOME) incomeCategories.last() else expenseCategories.last())
    }
}
