package com.example

import com.example.data.*
import com.example.util.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCurrencyFormatter() {
        CurrencyFormatter.activeCurrency = CurrencyFormatter.supportedCurrencies[0] // INR
        val formattedZero = CurrencyFormatter.formatInr(0.0)
        assertTrue(formattedZero.contains("₹"))

        val formattedThousands = CurrencyFormatter.formatInr(45000.0)
        assertTrue(formattedThousands.contains("45,000") || formattedThousands.contains("45000"))

        val compactLakh = CurrencyFormatter.formatCompact(150000.0)
        assertTrue(compactLakh.contains("L") || compactLakh.contains("₹"))

        // Test USD
        val usd = CurrencyFormatter.supportedCurrencies.first { it.code == "USD" }
        val usdFormatted = CurrencyFormatter.formatCurrency(250.0, usd)
        assertTrue(usdFormatted.contains("$"))
    }

    @Test
    fun testLocalizationManager() {
        val engDash = LocalizationManager.getString("nav_dashboard", "en")
        assertEquals("Dashboard", engDash)

        val hiDash = LocalizationManager.getString("nav_dashboard", "hi")
        assertEquals("डैशबोर्ड", hiDash)

        val bnDash = LocalizationManager.getString("nav_dashboard", "bn")
        assertEquals("ড্যাশবোর্ড", bnDash)
    }

    @Test
    fun testExpenseParser() {
        val expenseParsed = ExpenseParser.parseInput("Paid 350 for Swiggy dinner via UPI")
        assertEquals(350.0, expenseParsed.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, expenseParsed.type)
        assertEquals("Food & Dining", expenseParsed.category)
        assertEquals(PaymentMode.UPI, expenseParsed.paymentMode)

        val incomeParsed = ExpenseParser.parseInput("Salary credited 65000")
        assertEquals(65000.0, incomeParsed.amount, 0.01)
        assertEquals(TransactionType.INCOME, incomeParsed.type)
        assertEquals("Salary", incomeParsed.category)

        val fuelParsed = ExpenseParser.parseInput("Petrol fuel 1200 credit card")
        assertEquals(1200.0, fuelParsed.amount, 0.01)
        assertEquals("Transport & Fuel", fuelParsed.category)
        assertEquals(PaymentMode.CREDIT_CARD, fuelParsed.paymentMode)
    }

    @Test
    fun testDateUtils() {
        val currentMonth = DateUtils.getCurrentMonthKey()
        assertTrue(currentMonth.matches(Regex("""\d{4}-\d{2}""")))

        val prevMonth = DateUtils.getPreviousMonthKey("2026-10")
        assertEquals("2026-09", prevMonth)

        val nextMonth = DateUtils.getNextMonthKey("2026-10")
        assertEquals("2026-11", nextMonth)

        val (start, end) = DateUtils.getMonthStartAndEndTime("2026-10")
        assertTrue(end > start)
    }

    @Test
    fun testSessionInactivityCalculations() {
        val fifteenDaysMs = 15L * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()

        // 14 days ago: within limit
        val active14DaysAgo = now - (14L * 24L * 60L * 60L * 1000L)
        val elapsed14 = now - active14DaysAgo
        assertFalse("14 days should not expire", elapsed14 > fifteenDaysMs)
        val daysRemaining14 = 15 - (elapsed14 / (24L * 60L * 60L * 1000L)).toInt()
        assertEquals(1, daysRemaining14)

        // 16 days ago: expired
        val active16DaysAgo = now - (16L * 24L * 60L * 60L * 1000L)
        val elapsed16 = now - active16DaysAgo
        assertTrue("16 days should expire", elapsed16 > fifteenDaysMs)
    }

    @Test
    fun testNegativeRolloverFormatting() {
        val negativeBalance = -3500.0
        val formatted = CurrencyFormatter.formatCurrency(negativeBalance)
        assertTrue(formatted.contains("-") || formatted.contains("3,500") || formatted.contains("3500"))
    }

    @Test
    fun testStatementItemConversion() {
        val items = listOf(
            StatementItem(
                title = "DMart Supermarket",
                amount = 2500.0,
                type = TransactionType.EXPENSE,
                category = "Groceries",
                paymentMode = PaymentMode.UPI,
                timestamp = System.currentTimeMillis(),
                isSelected = true
            ),
            StatementItem(
                title = "Client Consulting Fee",
                amount = 15000.0,
                type = TransactionType.INCOME,
                category = "Business/Freelance",
                paymentMode = PaymentMode.NET_BANKING,
                timestamp = System.currentTimeMillis(),
                isSelected = false // not selected
            )
        )

        val transactions = StatementParser.toExpenseTransactions(items)
        assertEquals(1, transactions.size)
        assertEquals("DMart Supermarket", transactions[0].title)
        assertEquals(2500.0, transactions[0].amount, 0.01)
        assertEquals(TransactionType.EXPENSE, transactions[0].type)
        assertEquals("AI_STATEMENT", transactions[0].sourceTag)
    }

    @Test
    fun testExportFormats() {
        assertEquals(".csv", ExportManager.ExportFormat.SHEET.extension)
        assertEquals("text/csv", ExportManager.ExportFormat.SHEET.mimeType)
        assertEquals("Google Sheets (.csv)", ExportManager.ExportFormat.SHEET.label)
    }

    @Test
    fun testReceiptExtractionServiceDetails() {
        val sampleReceipt = "Starbucks Coffee Bill #104. Total Amount: 420.00 Date: 2025-05-14 Paid via UPI"
        val parsed = ExpenseParser.parseInput(sampleReceipt, sourceTag = "RECEIPT_LOCAL")
        assertEquals(420.0, parsed.amount, 0.01)
        assertEquals(PaymentMode.UPI, parsed.paymentMode)
        assertEquals(TransactionType.EXPENSE, parsed.type)

        val sampleLog = "HDFC Bank Alert: Rs 1499 debited on 15-05-2025 at AMAZON INDIA via UPI"
        val parsedLog = ExpenseParser.parseInput(sampleLog, sourceTag = "LOG_LOCAL")
        assertEquals(1499.0, parsedLog.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, parsedLog.type)
        assertEquals(PaymentMode.UPI, parsedLog.paymentMode)
    }

    @Test
    fun testExtractedReceiptDetailsModel() {
        val lineItems = listOf(
            ExtractedLineItem("Cappuccino", 240.0),
            ExtractedLineItem("Brownie", 180.0)
        )
        val details = ExtractedReceiptDetails(
            vendor = "Cafe Coffee Day",
            totalAmount = 420.0,
            date = "2025-05-14",
            timestamp = 1747200000000L,
            category = "Food & Dining",
            paymentMode = PaymentMode.UPI,
            type = TransactionType.EXPENSE,
            currency = "INR",
            lineItems = lineItems,
            confidenceNotes = "Extracted automatically by Gemini AI"
        )
        assertEquals("Cafe Coffee Day", details.vendor)
        assertEquals(420.0, details.totalAmount, 0.01)
        assertEquals("2025-05-14", details.date)
        assertEquals(2, details.lineItems.size)
        assertEquals("Food & Dining", details.category)
        assertEquals(PaymentMode.UPI, details.paymentMode)
    }
}


