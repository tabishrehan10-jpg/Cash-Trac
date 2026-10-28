package com.example

import com.example.data.ExpenseTransaction
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.example.util.CsvColumnMapping
import com.example.util.CsvTransactionParser
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class CsvTransactionParserTest {

    @Test
    fun testParseStandardSingleAmountCsv() {
        val csvContent = """
            Date,Description,Amount,Category,Payment Mode
            2026-10-15,Swiggy Delivery,450.00,Food,UPI
            2026-10-16,Salary Credit,85000.00,Income,Bank Transfer
            2026-10-17,Uber Ride,280.50,Transport,Credit Card
        """.trimIndent()

        val rawData = CsvTransactionParser.parseRawCsvFromText(csvContent)
        assertEquals(3, rawData.rows.size)

        val inferredMapping = CsvTransactionParser.inferColumnMapping(rawData.headers)
        assertEquals(0, inferredMapping.dateColumnIndex)
        assertEquals(1, inferredMapping.titleColumnIndex)
        assertEquals(2, inferredMapping.amountColumnIndex)
        assertFalse(inferredMapping.isSeparateDebitCredit)

        val parseResult = CsvTransactionParser.parseWithMapping(rawData, inferredMapping, emptyList())
        assertEquals(3, parseResult.items.size)
        assertEquals(0, parseResult.skippedRows)

        val t1 = parseResult.items[0]
        assertEquals("Swiggy Delivery", t1.title)
        assertEquals(450.0, t1.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, t1.type)
        assertEquals("Food & Dining", t1.category)

        val t2 = parseResult.items[1]
        assertEquals("Salary Credit", t2.title)
        assertEquals(85000.0, t2.amount, 0.01)
        assertEquals(TransactionType.INCOME, t2.type)

        val t3 = parseResult.items[2]
        assertEquals("Uber Ride", t3.title)
        assertEquals(280.50, t3.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, t3.type)
    }

    @Test
    fun testParseSeparateDebitCreditColumnsBankingCsv() {
        val csvContent = """
            Txn Date,Particulars,Withdrawal (Debit),Deposit (Credit),Balance
            05/10/2026,ACH DEBIT NETFLIX,649.00,,45200.00
            08/10/2026,UPI/REF/AIRTEL RECHARGE,719.00,,44481.00
            10/10/2026,NEFT CLIENT PAYMENT,,35000.00,79481.00
        """.trimIndent()

        val rawData = CsvTransactionParser.parseRawCsvFromText(csvContent)
        val inferredMapping = CsvTransactionParser.inferColumnMapping(rawData.headers)

        assertEquals(0, inferredMapping.dateColumnIndex)
        assertEquals(1, inferredMapping.titleColumnIndex)
        assertTrue("Should detect separate debit and credit columns", inferredMapping.isSeparateDebitCredit)
        assertEquals(2, inferredMapping.debitColumnIndex)
        assertEquals(3, inferredMapping.creditColumnIndex)

        val parseResult = CsvTransactionParser.parseWithMapping(rawData, inferredMapping, emptyList())
        assertEquals(3, parseResult.items.size)

        val netflix = parseResult.items[0]
        assertEquals("ACH DEBIT NETFLIX", netflix.title)
        assertEquals(649.0, netflix.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, netflix.type)

        val clientPayment = parseResult.items[2]
        assertEquals("NEFT CLIENT PAYMENT", clientPayment.title)
        assertEquals(35000.0, clientPayment.amount, 0.01)
        assertEquals(TransactionType.INCOME, clientPayment.type)
    }

    @Test
    fun testQuotedFieldsCommasAndCurrencySymbols() {
        val csvContent = """
            "Date","Vendor / Description","Amount","Notes"
            "12/10/2026","Reliance Retail, Phoenix Mall, Mumbai","₹4,250.75","Weekly grocery"
            "14/10/2026","Amazon Seller, Cloudtail","₹1,999.00","Books & stationery"
        """.trimIndent()

        val rawData = CsvTransactionParser.parseRawCsvFromText(csvContent)
        assertEquals(2, rawData.rows.size)
        assertEquals("Reliance Retail, Phoenix Mall, Mumbai", rawData.rows[0][1])

        val mapping = CsvTransactionParser.inferColumnMapping(rawData.headers)
        val parseResult = CsvTransactionParser.parseWithMapping(rawData, mapping, emptyList())

        assertEquals(2, parseResult.items.size)
        assertEquals(4250.75, parseResult.items[0].amount, 0.01)
        assertEquals("Reliance Retail, Phoenix Mall, Mumbai", parseResult.items[0].title)
        assertEquals("Weekly grocery", parseResult.items[0].note)
        assertEquals(1999.00, parseResult.items[1].amount, 0.01)
    }

    @Test
    fun testCustomColumnRemapping() {
        // CSV with arbitrary column orders and headers
        val csvContent = """
            Field1,Field2,Field3,Field4
            Groceries at BigBasket,2026-10-18,1850.50,Vegetables
            Dinner with friends,2026-10-19,2400.00,Social
        """.trimIndent()

        val rawData = CsvTransactionParser.parseRawCsvFromText(csvContent)

        // Custom mapping: Field2 = Date, Field1 = Title, Field3 = Amount, Field4 = Category
        val customMapping = CsvColumnMapping(
            dateColumnIndex = 1,
            titleColumnIndex = 0,
            amountColumnIndex = 2,
            categoryColumnIndex = 3,
            isSeparateDebitCredit = false
        )

        val parseResult = CsvTransactionParser.parseWithMapping(rawData, customMapping, emptyList())
        assertEquals(2, parseResult.items.size)

        val item1 = parseResult.items[0]
        assertEquals("Groceries at BigBasket", item1.title)
        assertEquals(1850.50, item1.amount, 0.01)
        assertEquals("Groceries", item1.category)
    }

    @Test
    fun testDuplicateDetectionAgainstExistingTransactions() {
        val existingTxDate = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).parse("20/10/2026")?.time ?: 0L
        val existing = listOf(
            ExpenseTransaction(
                id = 1,
                title = "Electricity Bill",
                amount = 2300.0,
                type = TransactionType.EXPENSE,
                category = "Bills & Utilities",
                paymentMode = PaymentMode.UPI,
                date = existingTxDate
            )
        )

        val csvContent = """
            Date,Description,Amount
            20/10/2026,Electricity Bill,2300.00
            21/10/2026,Water Bill,450.00
        """.trimIndent()

        val rawData = CsvTransactionParser.parseRawCsvFromText(csvContent)
        val mapping = CsvTransactionParser.inferColumnMapping(rawData.headers)
        val parseResult = CsvTransactionParser.parseWithMapping(rawData, mapping, existing)

        assertEquals(2, parseResult.items.size)
        // First row matches existing transaction title, amount, and date -> should be marked as duplicate
        assertTrue("Electricity bill should be marked as potential duplicate", parseResult.items[0].isDuplicate)
        assertFalse("Should be unselected by default if duplicate", parseResult.items[0].isSelected)

        // Second row is new -> not duplicate and selected by default
        assertFalse("Water bill should not be duplicate", parseResult.items[1].isDuplicate)
        assertTrue("New transactions should be selected by default", parseResult.items[1].isSelected)
    }

    @Test
    fun testSemicolonAndTabDelimiters() {
        val semicolonCsv = """
            Date;Description;Amount;Category
            2026-10-01;Metro Smart Card;500.00;Transit
            2026-10-02;Coffee Day;180.00;Food
        """.trimIndent()

        val rawDataSemicolon = CsvTransactionParser.parseRawCsvFromText(semicolonCsv)
        assertEquals(4, rawDataSemicolon.headers.size)
        assertEquals(2, rawDataSemicolon.rows.size)

        val mapping = CsvTransactionParser.inferColumnMapping(rawDataSemicolon.headers)
        val result = CsvTransactionParser.parseWithMapping(rawDataSemicolon, mapping, emptyList())
        assertEquals(2, result.items.size)
        assertEquals(500.0, result.items[0].amount, 0.01)
        assertEquals("Metro Smart Card", result.items[0].title)

        val tabCsv = "Date\tDescription\tAmount\n2026-10-05\tBook Store\t750.00"
        val rawDataTab = CsvTransactionParser.parseRawCsvFromText(tabCsv)
        assertEquals(3, rawDataTab.headers.size)
        assertEquals(1, rawDataTab.rows.size)
    }
}
