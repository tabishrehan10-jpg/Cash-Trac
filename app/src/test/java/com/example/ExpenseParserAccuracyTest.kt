package com.example

import com.example.data.TransactionType
import com.example.util.CsvTransactionParser
import com.example.util.ExpenseParser
import com.example.util.GeminiAiService
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExpenseParserAccuracyTest {

    private fun getFormattedDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    }

    @Test
    fun testFuelOn9Sep26Standard() {
        // "144 fuel on 9 sep 26" means: ₹144 ka 'fuel' on 9 september 2026
        val result = ExpenseParser.parseInput("144 fuel on 9 sep 26")

        assertEquals(144.0, result.amount, 0.01)
        assertEquals("Fuel", result.title)
        assertEquals("Transport & Fuel", result.category)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("2026-09-09", getFormattedDate(result.timestamp))
    }

    @Test
    fun testDateFirstFuelOn9Sep26() {
        // "9 sep 26, 144 on fuel" means: ₹144 ka 'fuel' on 9 september 2026
        val result = ExpenseParser.parseInput("9 sep 26, 144 on fuel")

        assertEquals(144.0, result.amount, 0.01)
        assertEquals("Fuel", result.title)
        assertEquals("Transport & Fuel", result.category)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("2026-09-09", getFormattedDate(result.timestamp))
    }

    @Test
    fun testNumericDateFuelOn9Sep26() {
        // "9/9/26, 144 on fuel" means: ₹144 ka 'fuel' on 9 september 2026
        val result = ExpenseParser.parseInput("9/9/26, 144 on fuel")

        assertEquals(144.0, result.amount, 0.01)
        assertEquals("Fuel", result.title)
        assertEquals("Transport & Fuel", result.category)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("2026-09-09", getFormattedDate(result.timestamp))
    }

    @Test
    fun testFruiteOn2Sep26CleanTitleAndCategory() {
        // User typed "2/9/26 fruite  ₹120"
        // Title should be "Fruit" (not "// fruite ₹"), amount 120.0, category "Groceries", date 2026-09-02
        val result = ExpenseParser.parseInput("2/9/26 fruite  ₹120")

        assertEquals(120.0, result.amount, 0.01)
        assertEquals("Fruit", result.title)
        assertEquals("Groceries", result.category)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("2026-09-02", getFormattedDate(result.timestamp))
    }

    @Test
    fun testMultiLineSingleMessageMultipleTransactions() {
        // Multi-line input should parse each line as its own transaction without merging descriptions
        val input = """
            144 fuel on 9 sep 26
            250 groceries on 10 sep 26
            50 tea
        """.trimIndent()

        val results = ExpenseParser.parseMultipleExpenses(input)
        assertEquals(3, results.size)

        // Transaction 1: Fuel
        val t1 = results[0]
        assertEquals(144.0, t1.amount, 0.01)
        assertEquals("Fuel", t1.title)
        assertEquals("Transport & Fuel", t1.category)
        assertEquals("2026-09-09", getFormattedDate(t1.timestamp))

        // Transaction 2: Groceries
        val t2 = results[1]
        assertEquals(250.0, t2.amount, 0.01)
        assertEquals("Groceries", t2.title)
        assertEquals("Groceries", t2.category)
        assertEquals("2026-09-10", getFormattedDate(t2.timestamp))

        // Transaction 3: Tea
        val t3 = results[2]
        assertEquals(50.0, t3.amount, 0.01)
        assertEquals("Tea", t3.title)
        assertEquals("Food & Dining", t3.category)
    }

    @Test
    fun testCommaSeparatedSingleMessageMultipleTransactions() {
        val input = "144 fuel on 9 sep 26, 250 groceries on 10 sep 26, 50 tea"
        val results = ExpenseParser.parseMultipleExpenses(input)

        assertEquals(3, results.size)
        assertEquals("Fuel", results[0].title)
        assertEquals(144.0, results[0].amount, 0.01)
        assertEquals("Groceries", results[1].title)
        assertEquals(250.0, results[1].amount, 0.01)
        assertEquals("Tea", results[2].title)
        assertEquals(50.0, results[2].amount, 0.01)
    }

    @Test
    fun testGeminiDateStringToTimestampSupport() {
        val ts1 = GeminiAiService.parseDateStringToTimestamp("2/9/26")
        assertEquals("2026-09-02", getFormattedDate(ts1))

        val ts2 = GeminiAiService.parseDateStringToTimestamp("9 sep 26")
        assertEquals("2026-09-09", getFormattedDate(ts2))

        val ts3 = GeminiAiService.parseDateStringToTimestamp("09/09/2026")
        assertEquals("2026-09-09", getFormattedDate(ts3))
    }

    @Test
    fun testCsvNewLineMeansNewTransaction() {
        val csv = """
            Date,Purpose of Expenses,Amount
            02/09/2026,Fruits and Vegetables,450.00
            05/09/2026,Shell Petrol Pump,1200.00
            08/09/2026,Milk and Dairy,120.00
        """.trimIndent()

        val raw = CsvTransactionParser.parseRawCsvFromText(csv)
        assertEquals(3, raw.rows.size)

        val mapping = CsvTransactionParser.inferColumnMapping(raw.headers)
        val result = CsvTransactionParser.parseWithMapping(raw, mapping)

        assertEquals(3, result.items.size)
        assertEquals("Fruits and Vegetables", result.items[0].title)
        assertEquals(450.0, result.items[0].amount, 0.01)
        assertEquals("Shell Petrol Pump", result.items[1].title)
        assertEquals(1200.0, result.items[1].amount, 0.01)
        assertEquals("Milk and Dairy", result.items[2].title)
        assertEquals(120.0, result.items[2].amount, 0.01)
    }
}
