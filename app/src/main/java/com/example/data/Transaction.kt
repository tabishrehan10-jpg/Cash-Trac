package com.example.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room Entity representing a financial transaction.
 *
 * @property id Unique transaction identifier (auto-generated primary key)
 * @property amount The financial value of the transaction in Double (e.g. ₹ INR)
 * @property category Transaction category (e.g. Food & Dining, Shopping, Salary)
 * @property type Transaction type indicating income or expense (TransactionType.INCOME / TransactionType.EXPENSE)
 * @property date Epoch timestamp in milliseconds representing the transaction date
 * @property isRecurring Flag indicating whether this is a recurring transaction (e.g. monthly rent, subscriptions)
 * @property title Short description or title of the transaction
 * @property paymentMode Mode of payment used (UPI, Cash, Credit Card, etc.)
 * @property note Optional notes or remarks
 * @property receiptUri Optional URI of attached photo receipt
 * @property sourceTag Origin source tag (MANUAL, VOICE, PHOTO, CHAT)
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val type: TransactionType,
    val date: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val title: String = "",
    val paymentMode: PaymentMode = PaymentMode.UPI,
    val note: String = "",
    val receiptUri: String? = null,
    val sourceTag: String = "MANUAL"
) {
    /**
     * Backward-compatibility alias for the transaction timestamp.
     */
    val timestamp: Long
        @Ignore
        get() = date

    /**
     * Secondary constructor supporting existing callers who provide a `timestamp`
     * and optional constructor fields.
     */
    @Ignore
    constructor(
        id: Long = 0,
        title: String = "",
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMode: PaymentMode = PaymentMode.UPI,
        timestamp: Long = System.currentTimeMillis(),
        note: String = "",
        receiptUri: String? = null,
        sourceTag: String = "MANUAL",
        isRecurring: Boolean = false
    ) : this(
        id = id,
        amount = amount,
        category = category,
        type = type,
        date = timestamp,
        isRecurring = isRecurring,
        title = title,
        paymentMode = paymentMode,
        note = note,
        receiptUri = receiptUri,
        sourceTag = sourceTag
    )
}

/**
 * Typealias to maintain complete compatibility across all existing UI components,
 * repositories, and utilities.
 */
typealias ExpenseTransaction = Transaction
