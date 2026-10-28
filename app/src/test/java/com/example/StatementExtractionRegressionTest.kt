package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExpenseTransaction
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.example.util.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatementExtractionRegressionTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /**
     * Test Scenario 5 & CUJ: September 2026 spending test document
     * Reported starting balance: ₹138,693
     * Total expenses: ₹17,241
     * Money left over: ₹121,452
     *
     * Validates:
     * - Starting balance ₹138,693 is NOT an expense or transaction row.
     * - Money left over ₹121,452 is NOT an expense or transaction row.
     * - Total expenses ₹17,241 is reconciled with the sum of individual debit entries.
     * - Reconciliation status is RECONCILED.
     */
    @Test
    fun testSeptember2026SpendingDocumentBalanceSeparationAndReconciliation() {
        val sampleStatementText = """
            Account Statement - Monthly spending of September 2026
            Customer: Alex Doe
            Starting balance: ₹138,693.00
            Total spending: ₹17,241.00
            Money left over: ₹121,452.00
            
            TRANSACTIONS:
            02/09/2026 Reliance Fresh Supermarket ₹4,350.00 debit
            05/09/2026 Swiggy Food Orders ₹1,890.00 debit
            10/09/2026 Shell Petrol Bunk ₹2,500.00 debit
            15/09/2026 Electricity BESCOM ₹3,100.00 debit
            20/09/2026 Apollo Pharmacy Medicines ₹950.00 debit
            24/09/2026 Amazon India Shopping ₹3,451.00 debit
            28/09/2026 Cafe Coffee Day ₹1,000.00 debit
        """.trimIndent()

        val parsed = StatementParser.parseTextStatementFallback(sampleStatementText, "Monthly spending of September 2026.pdf")

        // 1. Verify balances and reported totals
        assertEquals(138693.0, parsed.openingBalance ?: 0.0, 0.01)
        assertEquals(121452.0, parsed.closingBalance ?: 0.0, 0.01)
        assertEquals(17241.0, parsed.reportedTotalExpenses ?: 0.0, 0.01)

        // 2. Verify no balance summary row leaked into transactions
        val hasStartingBalanceTx = parsed.transactions.any { it.amount == 138693.0 || it.title.contains("starting", ignoreCase = true) }
        val hasLeftoverBalanceTx = parsed.transactions.any { it.amount == 121452.0 || it.title.contains("left over", ignoreCase = true) }
        val hasTotalExpenseRowTx = parsed.transactions.any { it.title.contains("total spending", ignoreCase = true) }

        assertFalse("Starting balance must NOT be recorded as a transaction", hasStartingBalanceTx)
        assertFalse("Money left over must NOT be recorded as a transaction", hasLeftoverBalanceTx)
        assertFalse("Total spending row must NOT be recorded as a transaction", hasTotalExpenseRowTx)

        // 3. Verify individual transactions
        assertEquals(7, parsed.transactions.size)
        val calculatedSum = parsed.transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(17241.0, calculatedSum, 0.01)

        // 4. Verify reconciliation check
        val diff = Math.abs(calculatedSum - (parsed.reportedTotalExpenses ?: 0.0))
        val reconStatus = if (diff < 1.0) ReconciliationStatus.RECONCILED else ReconciliationStatus.DISCREPANCY
        assertEquals(ReconciliationStatus.RECONCILED, reconStatus)
    }

    /**
     * Test Scenario 1: Searchable PDF / Bank Statement with debit & credit lines.
     */
    @Test
    fun testSearchableStatementParsingWithDebitsAndCredits() {
        val jsonPayload = """
            {
              "document_type": "bank_statement",
              "currency": "INR",
              "opening_balance": 50000.0,
              "closing_balance": 85000.0,
              "reported_total_expenses": 15000.0,
              "reported_total_income": 50000.0,
              "transactions": [
                {
                  "transaction_date": "2026-09-01",
                  "description": "Monthly Salary Direct Deposit",
                  "amount": 50000.0,
                  "transaction_type": "credit",
                  "category": "Salary",
                  "payment_method": "Net Banking",
                  "source_page": 1,
                  "confidence": 0.98,
                  "needs_review": false
                },
                {
                  "transaction_date": "2026-09-05",
                  "description": "House Rent Maintenance",
                  "amount": 15000.0,
                  "transaction_type": "debit",
                  "category": "Housing & Rent",
                  "payment_method": "UPI",
                  "source_page": 1,
                  "confidence": 0.96,
                  "needs_review": false
                }
              ],
              "document_warnings": []
            }
        """.trimIndent()

        val result = GeminiAiService.parseJsonToDocumentResult(jsonPayload)
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(50000.0, result.openingBalance ?: 0.0, 0.01)
        assertEquals(15000.0, result.reportedTotalExpenses ?: 0.0, 0.01)
        assertEquals(TransactionType.INCOME, result.transactions[0].type)
        assertEquals(TransactionType.EXPENSE, result.transactions[1].type)
    }

    /**
     * Test Scenario 3: Single Receipt Image JSON structure.
     */
    @Test
    fun testSingleReceiptImageExtraction() {
        val jsonReceipt = """
            {
              "document_type": "receipt",
              "currency": "INR",
              "reported_total_expenses": 650.0,
              "transactions": [
                {
                  "transaction_date": "2026-09-12",
                  "description": "Starbucks Coffee & Croissant",
                  "amount": 650.0,
                  "transaction_type": "debit",
                  "category": "Food & Dining",
                  "payment_method": "Credit Card",
                  "confidence": 0.95,
                  "needs_review": false
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiAiService.parseJsonToDocumentResult(jsonReceipt)
        assertTrue(parsed.isSuccess)
        assertEquals(1, parsed.transactions.size)
        assertEquals("Starbucks Coffee & Croissant", parsed.transactions[0].title)
        assertEquals(650.0, parsed.transactions[0].amount, 0.01)
        assertEquals(PaymentMode.CREDIT_CARD, parsed.transactions[0].paymentMode)
    }

    /**
     * Test Scenario 4: Multi-page document with source_page attribution.
     */
    @Test
    fun testMultiPageDocumentAttribution() {
        val multiPageJson = """
            {
              "document_type": "bank_statement",
              "currency": "INR",
              "transactions": [
                {
                  "transaction_date": "2026-09-02",
                  "description": "Page 1 Purchase D-Mart",
                  "amount": 2500.0,
                  "transaction_type": "debit",
                  "category": "Groceries",
                  "payment_method": "UPI",
                  "source_page": 1
                },
                {
                  "transaction_date": "2026-09-18",
                  "description": "Page 2 Purchase Fuel Shell",
                  "amount": 1800.0,
                  "transaction_type": "debit",
                  "category": "Transport & Fuel",
                  "payment_method": "Credit Card",
                  "source_page": 2
                }
              ]
            }
        """.trimIndent()

        val result = GeminiAiService.parseJsonToDocumentResult(multiPageJson)
        assertEquals(2, result.transactions.size)
        assertEquals(1, result.transactions[0].sourcePage)
        assertEquals(2, result.transactions[1].sourcePage)
    }

    /**
     * Test Scenario 7: Ambiguous or low-confidence rows flagged for review.
     */
    @Test
    fun testAmbiguousOrLowConfidenceRowsFlagged() {
        val ambiguousJson = """
            {
              "document_type": "bank_statement",
              "transactions": [
                {
                  "transaction_date": "2026-09-10",
                  "description": "BLURRY POS 98327***",
                  "amount": 420.0,
                  "transaction_type": "debit",
                  "category": "Other Expense",
                  "confidence": 0.55,
                  "needs_review": true
                }
              ],
              "document_warnings": ["Row 1 had low OCR clarity; flagged for user review."]
            }
        """.trimIndent()

        val parsed = GeminiAiService.parseJsonToDocumentResult(ambiguousJson)
        assertEquals(1, parsed.transactions.size)
        assertTrue(parsed.transactions[0].needsReview)
        assertEquals(1, parsed.documentWarnings.size)
    }

    /**
     * Test Scenario 8: Duplicate detection against existing ledger records.
     */
    @Test
    fun testDuplicateDetectionOnReImport() {
        val timestamp = System.currentTimeMillis()
        val existingLedger = listOf(
            ExpenseTransaction(
                id = 101L,
                title = "Swiggy Dinner Order",
                amount = 450.0,
                type = TransactionType.EXPENSE,
                category = "Food & Dining",
                paymentMode = PaymentMode.UPI,
                timestamp = timestamp
            )
        )

        val newItems = listOf(
            StatementItem(
                title = "Swiggy Dinner Order",
                amount = 450.0,
                type = TransactionType.EXPENSE,
                category = "Food & Dining",
                paymentMode = PaymentMode.UPI,
                timestamp = timestamp
            ),
            StatementItem(
                title = "New Uber Cab Ride",
                amount = 220.0,
                type = TransactionType.EXPENSE,
                category = "Transport & Fuel",
                paymentMode = PaymentMode.UPI,
                timestamp = timestamp
            )
        )

        val processed = StatementParser.markDuplicates(newItems, existingLedger)
        assertEquals(2, processed.size)

        // First item should be detected as duplicate and deselected by default
        assertTrue(processed[0].isDuplicate)
        assertFalse(processed[0].isSelected)

        // Second item is new and selected
        assertFalse(processed[1].isDuplicate)
        assertTrue(processed[1].isSelected)
    }

    /**
     * Test Scenario 9: Deterministic transaction fingerprint consistency.
     */
    @Test
    fun testTransactionFingerprintDeterministic() {
        val fp1 = GeminiAiService.computeTransactionFingerprint("2026-09-15", "Nature's Basket", 1450.50, TransactionType.EXPENSE)
        val fp2 = GeminiAiService.computeTransactionFingerprint("2026-09-15", "natures basket", 1450.50, TransactionType.EXPENSE)
        assertEquals(fp1, fp2)
    }
}
