package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.Categories
import com.example.data.ExpenseTransaction
import com.example.data.PaymentMode
import com.example.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "CsvTransactionParser"

/**
 * Raw representation of a parsed CSV file.
 */
data class CsvRawData(
    val fileName: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

/**
 * User-configurable column mapping schema to map CSV columns to the Transaction model.
 */
data class CsvColumnMapping(
    val dateColumnIndex: Int = -1,
    val titleColumnIndex: Int = -1,
    val amountColumnIndex: Int = -1,
    val debitColumnIndex: Int = -1,
    val creditColumnIndex: Int = -1,
    val categoryColumnIndex: Int = -1,
    val paymentModeColumnIndex: Int = -1,
    val notesColumnIndex: Int = -1,
    val typeColumnIndex: Int = -1,
    val isSeparateDebitCredit: Boolean = false,
    val defaultCategory: String = "Other Expense",
    val defaultPaymentMode: PaymentMode = PaymentMode.UPI,
    val dateFormatPattern: String = "AUTO"
)

/**
 * Result of mapping and parsing CSV records into application transaction items.
 */
data class CsvParseResult(
    val fileName: String,
    val totalRows: Int,
    val validRows: Int,
    val skippedRows: Int,
    val totalDebits: Double,
    val totalCredits: Double,
    val items: List<StatementItem>,
    val warnings: List<String> = emptyList()
)

object CsvTransactionParser {

    /**
     * Reads a CSV file from URI and extracts headers and raw cell rows.
     */
    suspend fun readCsvFile(context: Context, uri: Uri): CsvRawData = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } ?: throw IllegalArgumentException("Could not open CSV file stream.")

        parseRawCsvFromText(text, fileName)
    }

    /**
     * Parses CSV text directly into CsvRawData.
     */
    fun parseRawCsvFromText(csvContent: String, fileName: String = "import.csv"): CsvRawData {
        val allLines = csvContent.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("sep=") }

        if (allLines.isEmpty()) {
            throw IllegalArgumentException("The selected CSV content is empty.")
        }

        // Detect delimiter (comma or tab or semicolon)
        val firstFew = allLines.take(3).joinToString("\n")
        val delimiter = when {
            firstFew.count { it == '\t' } > firstFew.count { it == ',' } -> '\t'
            firstFew.count { it == ';' } > firstFew.count { it == ',' } -> ';'
            else -> ','
        }

        val parsedTokens = allLines.map { parseCsvLine(it, delimiter) }
        val headers = parsedTokens[0].map { it.trim().removePrefix("\"").removeSuffix("\"").trim() }
        val dataRows = parsedTokens.drop(1).filter { row -> row.any { it.isNotBlank() } }

        return CsvRawData(
            fileName = fileName,
            headers = headers,
            rows = dataRows
        )
    }

    /**
     * Intelligently infers optimal column mapping based on standard banking and accounting headers.
     */
    fun inferColumnMapping(headers: List<String>): CsvColumnMapping {
        var dateIdx = -1
        var titleIdx = -1
        var amountIdx = -1
        var debitIdx = -1
        var creditIdx = -1
        var categoryIdx = -1
        var modeIdx = -1
        var notesIdx = -1
        var typeIdx = -1

        val lowerHeaders = headers.map { it.lowercase().trim().replace(Regex("[^a-z0-9]"), "_") }

        lowerHeaders.forEachIndexed { index, h ->
            when {
                dateIdx == -1 && (h.contains("date") || h.contains("time") || h.contains("txn_dt") || h.contains("value_dt")) ->
                    dateIdx = index

                (h.contains("debit") || h.contains("withdrawal") || h.contains("money_out") || h.contains("paid_out") || h == "dr") && debitIdx == -1 ->
                    debitIdx = index

                (h.contains("credit") || h.contains("deposit") || h.contains("money_in") || h.contains("paid_in") || h == "cr") && creditIdx == -1 ->
                    creditIdx = index

                amountIdx == -1 && (h.contains("amount") || h.contains("txn_amt") || h.contains("transaction_amt") || h == "amt" || h == "value") ->
                    amountIdx = index

                titleIdx == -1 && (h.contains("desc") || h.contains("particular") || h.contains("narration") ||
                        h.contains("merchant") || h.contains("payee") || h.contains("title") ||
                        h.contains("details") || h.contains("entity") || h.contains("name")) ->
                    titleIdx = index

                categoryIdx == -1 && (h.contains("category") || h.contains("sub_category") || h.contains("expense_type") || h.contains("tag")) ->
                    categoryIdx = index

                modeIdx == -1 && (h.contains("mode") || h.contains("channel") || h.contains("payment_method") || h.contains("type_of_txn")) ->
                    modeIdx = index

                typeIdx == -1 && (h == "type" || h == "txn_type" || h == "transaction_type" || h == "cr_dr") ->
                    typeIdx = index

                notesIdx == -1 && (h.contains("note") || h.contains("remark") || h.contains("ref") || h.contains("memo")) ->
                    notesIdx = index
            }
        }

        // If no title column was found, pick the first non-numeric/non-date column
        if (titleIdx == -1) {
            lowerHeaders.forEachIndexed { index, h ->
                if (index != dateIdx && index != amountIdx && index != debitIdx && index != creditIdx && !h.contains("id") && !h.contains("no")) {
                    titleIdx = index
                    return@forEachIndexed
                }
            }
        }

        val hasSeparateDebitCredit = debitIdx != -1 || creditIdx != -1

        return CsvColumnMapping(
            dateColumnIndex = dateIdx,
            titleColumnIndex = if (titleIdx != -1) titleIdx else 0,
            amountColumnIndex = amountIdx,
            debitColumnIndex = debitIdx,
            creditColumnIndex = creditIdx,
            categoryColumnIndex = categoryIdx,
            paymentModeColumnIndex = modeIdx,
            notesColumnIndex = notesIdx,
            typeColumnIndex = typeIdx,
            isSeparateDebitCredit = hasSeparateDebitCredit
        )
    }

    /**
     * Parses raw rows into validated StatementItems using the specified CsvColumnMapping.
     */
    fun parseWithMapping(
        rawData: CsvRawData,
        mapping: CsvColumnMapping,
        existingTransactions: List<ExpenseTransaction> = emptyList()
    ): CsvParseResult {
        val items = mutableListOf<StatementItem>()
        val warnings = mutableListOf<String>()
        var skippedCount = 0

        for ((rowIndex, row) in rawData.rows.withIndex()) {
            if (row.isEmpty() || row.all { it.isBlank() }) {
                skippedCount++
                continue
            }

            var amount = 0.0
            var type = TransactionType.EXPENSE
            var title = "Transaction ${rowIndex + 1}"
            var timestamp = System.currentTimeMillis()
            var dateStr = ""
            var category = mapping.defaultCategory
            var paymentMode = mapping.defaultPaymentMode
            var notes = ""

            // 1. Amount & Transaction Type
            if (mapping.isSeparateDebitCredit) {
                var debitVal = 0.0
                var creditVal = 0.0

                if (mapping.debitColumnIndex in row.indices && row[mapping.debitColumnIndex].isNotBlank()) {
                    debitVal = cleanNumericAmount(row[mapping.debitColumnIndex])
                }
                if (mapping.creditColumnIndex in row.indices && row[mapping.creditColumnIndex].isNotBlank()) {
                    creditVal = cleanNumericAmount(row[mapping.creditColumnIndex])
                }

                if (debitVal > 0.0) {
                    amount = debitVal
                    type = TransactionType.EXPENSE
                } else if (creditVal > 0.0) {
                    amount = creditVal
                    type = TransactionType.INCOME
                } else if (mapping.amountColumnIndex in row.indices) {
                    val rawAmt = cleanNumericAmount(row[mapping.amountColumnIndex])
                    amount = Math.abs(rawAmt)
                    type = if (rawAmt < 0.0) TransactionType.EXPENSE else TransactionType.INCOME
                }
            } else {
                if (mapping.amountColumnIndex in row.indices && row[mapping.amountColumnIndex].isNotBlank()) {
                    val rawStr = row[mapping.amountColumnIndex]
                    val parsedAmt = cleanNumericAmount(rawStr)
                    amount = Math.abs(parsedAmt)

                    // Check if row has explicit negative or debit sign
                    if (rawStr.contains("-") || rawStr.contains("(") || rawStr.contains("dr", ignoreCase = true)) {
                        type = TransactionType.EXPENSE
                    } else if (rawStr.contains("+") || rawStr.contains("cr", ignoreCase = true)) {
                        type = TransactionType.INCOME
                    } else if (mapping.typeColumnIndex in row.indices) {
                        val tStr = row[mapping.typeColumnIndex].lowercase()
                        type = if (tStr.contains("credit") || tStr.contains("income") || tStr.contains("cr") || tStr.contains("deposit")) {
                            TransactionType.INCOME
                        } else {
                            TransactionType.EXPENSE
                        }
                    }
                }
            }

            // Fallback: search all columns for positive numeric amount
            if (amount <= 0.0) {
                for ((colIdx, cell) in row.withIndex()) {
                    if (colIdx != mapping.dateColumnIndex && colIdx != mapping.titleColumnIndex) {
                        val testVal = cleanNumericAmount(cell)
                        if (testVal > 0.0) {
                            amount = testVal
                            break
                        }
                    }
                }
            }

            if (amount <= 0.0) {
                skippedCount++
                continue // Row has no valid transaction amount
            }

            // 2. Title / Description
            if (mapping.titleColumnIndex in row.indices && row[mapping.titleColumnIndex].isNotBlank()) {
                val rawTitle = row[mapping.titleColumnIndex].trim().removePrefix("\"").removeSuffix("\"").trim()
                if (rawTitle.isNotBlank()) title = rawTitle
            }

            // Infer income type if not explicitly designated and title or category indicates income
            if (!mapping.isSeparateDebitCredit && (mapping.typeColumnIndex !in row.indices || row[mapping.typeColumnIndex].isBlank())) {
                val lowerTitle = title.lowercase()
                val rawCat = if (mapping.categoryColumnIndex in row.indices) row[mapping.categoryColumnIndex].lowercase() else ""
                if (rawCat.contains("income") || rawCat.contains("salary") ||
                    lowerTitle.contains("salary") || lowerTitle.contains("dividend") ||
                    lowerTitle.contains("refund") || lowerTitle.contains("interest credited") ||
                    lowerTitle.contains("cashback received") || lowerTitle.contains("credit from")
                ) {
                    type = TransactionType.INCOME
                }
            }

            // 3. Date
            if (mapping.dateColumnIndex in row.indices && row[mapping.dateColumnIndex].isNotBlank()) {
                dateStr = row[mapping.dateColumnIndex].trim()
                val parsedTime = parseDate(dateStr, mapping.dateFormatPattern)
                if (parsedTime > 0L) {
                    timestamp = parsedTime
                }
            }

            // 4. Category
            if (mapping.categoryColumnIndex in row.indices && row[mapping.categoryColumnIndex].isNotBlank()) {
                val catStr = row[mapping.categoryColumnIndex].trim()
                category = GeminiAiService.sanitizeCategory(catStr, type)
            } else {
                val autoCat = ExpenseParser.parseInput(title).category
                category = if (autoCat.isNotBlank() && autoCat != "Other Expense") autoCat else mapping.defaultCategory
            }

            // 5. Payment Mode
            if (mapping.paymentModeColumnIndex in row.indices && row[mapping.paymentModeColumnIndex].isNotBlank()) {
                paymentMode = GeminiAiService.sanitizePaymentMode(row[mapping.paymentModeColumnIndex])
            } else {
                val lowerTitle = title.lowercase()
                paymentMode = when {
                    lowerTitle.contains("upi") || lowerTitle.contains("gpay") || lowerTitle.contains("phonepe") -> PaymentMode.UPI
                    lowerTitle.contains("card") || lowerTitle.contains("pos") -> PaymentMode.CREDIT_CARD
                    lowerTitle.contains("cash") || lowerTitle.contains("atm") -> PaymentMode.CASH
                    lowerTitle.contains("neft") || lowerTitle.contains("rtgs") || lowerTitle.contains("imps") || lowerTitle.contains("bank") -> PaymentMode.NET_BANKING
                    else -> mapping.defaultPaymentMode
                }
            }

            // 6. Notes
            if (mapping.notesColumnIndex in row.indices && row[mapping.notesColumnIndex].isNotBlank()) {
                notes = row[mapping.notesColumnIndex].trim()
            }

            val fp = GeminiAiService.computeTransactionFingerprint(dateStr, title, amount, type)

            items.add(
                StatementItem(
                    id = UUID.randomUUID().toString(),
                    fingerprint = fp,
                    title = title,
                    amount = amount,
                    type = type,
                    category = category,
                    paymentMode = paymentMode,
                    timestamp = timestamp,
                    sourcePage = 1,
                    confidence = 0.95f,
                    needsReview = false,
                    isDuplicate = false,
                    isSelected = true,
                    note = notes.ifBlank { "CSV row ${rowIndex + 1}" },
                    originalText = row.joinToString(" | ").take(100)
                )
            )
        }

        // Deduplicate against existing ledger
        val deduplicated = StatementParser.markDuplicates(items, existingTransactions)

        val totalDebits = deduplicated.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalCredits = deduplicated.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

        return CsvParseResult(
            fileName = rawData.fileName,
            totalRows = rawData.rows.size,
            validRows = deduplicated.size,
            skippedRows = skippedCount,
            totalDebits = totalDebits,
            totalCredits = totalCredits,
            items = deduplicated,
            warnings = warnings
        )
    }

    fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line.toCharArray()) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens.map { it.removePrefix("\"").removeSuffix("\"").trim() }
    }

    fun cleanNumericAmount(raw: String): Double {
        val cleaned = raw.replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace(",", "")
            .replace("dr", "", ignoreCase = true)
            .replace("cr", "", ignoreCase = true)
            .replace("(", "-")
            .replace(")", "")
            .replace("/", "")
            .trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun parseDate(dateStr: String, userPattern: String = "AUTO"): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()

        if (userPattern != "AUTO") {
            try {
                val sdf = SimpleDateFormat(userPattern, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null && date.time > 0L) return date.time
            } catch (_: Exception) {}
        }

        val patterns = listOf(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "dd/MM/yy",
            "dd-MMM-yyyy",
            "dd MMM yyyy",
            "yyyy/MM/dd",
            "dd.MM.yyyy",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null && date.time > 0L) return date.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "transactions_${System.currentTimeMillis()}.csv"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank()) {
                name = segment
            }
        }
        return name
    }
}
