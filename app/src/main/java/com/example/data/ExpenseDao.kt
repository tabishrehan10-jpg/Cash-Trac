package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getTransactionsBetweenFlow(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC")
    suspend fun getTransactionsBetween(startTime: Long, endTime: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE isRecurring = 1 ORDER BY date DESC")
    fun getRecurringTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isRecurring = 1 ORDER BY date DESC")
    suspend fun getRecurringTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

typealias TransactionDao = ExpenseDao

@Dao
interface BudgetDao {
    @Query("SELECT * FROM month_budgets WHERE monthKey = :monthKey")
    fun getBudgetFlow(monthKey: String): Flow<MonthBudgetConfig?>

    @Query("SELECT * FROM month_budgets WHERE monthKey = :monthKey")
    suspend fun getBudget(monthKey: String): MonthBudgetConfig?

    @Query("SELECT * FROM month_budgets")
    suspend fun getAllBudgets(): List<MonthBudgetConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(config: MonthBudgetConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBudgets(budgets: List<MonthBudgetConfig>)

    @Query("DELETE FROM month_budgets")
    suspend fun clearAllBudgets()
}
