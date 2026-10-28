package com.example.util

import com.example.data.Categories
import com.example.data.PaymentMode
import com.example.data.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedExpense(
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val paymentMode: PaymentMode,
    val confidence: Float,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String = ""
)

object ExpenseParser {

    private val explicitAmountRegex = Pattern.compile(
        """(?:total(?:\s+amount)?[:\s]*|rs\.?|inr|₹)\s*([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?|\b[0-9]+(?:\.[0-9]{1,2})?\s*(?:k|lakh|lac)?)\s*(?:rs\.?|inr|rupees)?""",
        Pattern.CASE_INSENSITIVE
    )

    private val amountRegex = Pattern.compile(
        """(?:rs\.?|inr|₹)?\s*([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?|\b[0-9]+(?:\.[0-9]{1,2})?\s*(?:k|lakh|lac)?)\s*(?:rs\.?|inr|rupees)?""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses multiple expenses from a single multi-line or compound message.
     * E.g.
     * "144 fuel on 9 sep 26\n250 groceries on 10 sep 26\n50 tea"
     * or "144 fuel on 9 sep 26, 250 groceries on 10 sep 26, 50 tea"
     */
    fun parseMultipleExpenses(input: String, sourceTag: String = "CHAT"): List<ParsedExpense> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Check if input contains multiple lines
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size > 1) {
            val results = lines.mapNotNull { line ->
                val p = parseSingleInput(line, sourceTag)
                if (p.amount > 0.0) p else null
            }
            if (results.isNotEmpty()) return results
        }

        // 2. Check if single line has multiple items separated by semicolons, commas, or "and"
        val commaSegments = trimmed.split(Regex("""(?i)\s*;\s*|\s*,\s*|\s+and\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (commaSegments.size > 1) {
            val validSegments = commaSegments.mapNotNull { seg ->
                // Check if segment has actual monetary amount AFTER extracting any date
                val (_, _, textWithoutDate) = extractDateFromText(seg)
                val (amt, _) = extractAmountFromText(textWithoutDate)
                if (amt > 0.0) {
                    val p = parseSingleInput(seg, sourceTag)
                    if (p.amount > 0.0) p else null
                } else null
            }
            // If at least 2 distinct segments have valid amounts, return them separately
            if (validSegments.size >= 2) {
                return validSegments
            }
        }

        // 3. Single transaction
        return listOf(parseSingleInput(trimmed, sourceTag))
    }

    /**
     * Backward-compatible single item parser.
     */
    fun parseInput(input: String, sourceTag: String = "CHAT"): ParsedExpense {
        val multiple = parseMultipleExpenses(input, sourceTag)
        return multiple.firstOrNull() ?: parseSingleInput(input, sourceTag)
    }

    private fun parseSingleInput(input: String, sourceTag: String): ParsedExpense {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Detect and extract Date
        val (detectedTimestamp, detectedDateStr, textWithoutDate) = extractDateFromText(trimmed)

        // 2. Detect Amount
        val (amount, textWithoutAmount) = extractAmountFromText(textWithoutDate)
        val finalAmount = if (amount > 0.0) amount else 0.0

        // 3. Detect Transaction Type (Expense vs Income)
        val textWithoutPaymentModes = lower
            .replace("credit card", "")
            .replace("creditcard", "")
            .replace("debit card", "")
            .replace("debitcard", "")

        val incomeKeywords = listOf(
            "received", "got", "credited", "salary", "dividend", "cashback",
            "bonus", "refund", "earned", "income", "interest credited"
        )
        val isIncome = incomeKeywords.any { textWithoutPaymentModes.contains(it) } ||
                Regex("""\bcredit\b""").containsMatchIn(textWithoutPaymentModes)
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 4. Detect Payment Mode
        val paymentMode = when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> PaymentMode.UPI
            lower.contains("cash") -> PaymentMode.CASH
            lower.contains("credit card") || lower.contains("creditcard") || lower.contains(" cc ") -> PaymentMode.CREDIT_CARD
            lower.contains("debit card") || lower.contains("debitcard") || lower.contains(" dc ") -> PaymentMode.DEBIT_CARD
            lower.contains("net banking") || lower.contains("bank transfer") || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps") -> PaymentMode.NET_BANKING
            else -> PaymentMode.UPI
        }

        // 5. Detect Category
        val category = if (type == TransactionType.INCOME) {
            detectIncomeCategory(lower)
        } else {
            detectExpenseCategory(lower)
        }

        // 6. Clean Title
        val title = generateCleanTitle(textWithoutAmount, category, type)

        return ParsedExpense(
            title = title,
            amount = finalAmount,
            type = type,
            category = category,
            paymentMode = paymentMode,
            confidence = 0.95f,
            rawText = input,
            timestamp = detectedTimestamp,
            dateString = detectedDateStr
        )
    }

    private data class DateExtractionResult(
        val timestamp: Long,
        val dateString: String,
        val textWithoutDate: String
    )

    private fun extractDateFromText(input: String): DateExtractionResult {
        var working = input

        // Check relative dates: today / yesterday
        val todayMatcher = Pattern.compile("""(?i)\btoday\b""").matcher(working)
        if (todayMatcher.find()) {
            val ts = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))
            val cleaned = todayMatcher.replaceAll(" ").trim()
            return DateExtractionResult(ts, dateStr, cleaned)
        }

        val yesterdayMatcher = Pattern.compile("""(?i)\byesterday\b""").matcher(working)
        if (yesterdayMatcher.find()) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val ts = cal.timeInMillis
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))
            val cleaned = yesterdayMatcher.replaceAll(" ").trim()
            return DateExtractionResult(ts, dateStr, cleaned)
        }

        // 1. Text Month Pattern: e.g. "9 sep 26", "9 sep 2026", "09-sep-2026", "9th september 2026", "on 9 sep 26"
        val textMonthRegex = Pattern.compile(
            """(?i)(?:on\s+)?\b(\d{1,2})(?:st|nd|rd|th)?[\s\/\.-]+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)[\s\/\.-]+(\d{2,4})\b"""
        )
        val tmMatcher = textMonthRegex.matcher(working)
        if (tmMatcher.find()) {
            val day = tmMatcher.group(1)?.toIntOrNull() ?: 1
            val monthRaw = tmMatcher.group(2) ?: "jan"
            var year = tmMatcher.group(3)?.toIntOrNull() ?: 2026
            if (year < 100) year += 2000

            val monthIndex = parseMonthNameToIndex(monthRaw)
            val cal = Calendar.getInstance()
            cal.set(year, monthIndex, day, 12, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val ts = cal.timeInMillis
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            val matchedEntire = tmMatcher.group(0) ?: ""
            val cleaned = working.replace(matchedEntire, " ").trim()
            return DateExtractionResult(ts, dateStr, cleaned)
        }

        // 2. Month Text first: e.g. "sep 9 2026", "september 9, 2026", "sep 9 26"
        val monthFirstRegex = Pattern.compile(
            """(?i)(?:on\s+)?\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{2,4}))?\b"""
        )
        val mfMatcher = monthFirstRegex.matcher(working)
        if (mfMatcher.find()) {
            val monthRaw = mfMatcher.group(1) ?: "jan"
            val day = mfMatcher.group(2)?.toIntOrNull() ?: 1
            var year = mfMatcher.group(3)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            if (year < 100) year += 2000

            val monthIndex = parseMonthNameToIndex(monthRaw)
            val cal = Calendar.getInstance()
            cal.set(year, monthIndex, day, 12, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val ts = cal.timeInMillis
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            val matchedEntire = mfMatcher.group(0) ?: ""
            val cleaned = working.replace(matchedEntire, " ").trim()
            return DateExtractionResult(ts, dateStr, cleaned)
        }

        // 3. Numeric Date Pattern (Indian standard: DD/MM/YY or DD/MM/YYYY): e.g. "9/9/26", "2/9/26", "02/09/2026", "9-9-26"
        val numericDateRegex = Pattern.compile(
            """(?i)(?:on\s+)?\b(\d{1,2})[\/\.-](\d{1,2})[\/\.-](\d{2,4})\b"""
        )
        val numMatcher = numericDateRegex.matcher(working)
        if (numMatcher.find()) {
            val d = numMatcher.group(1)?.toIntOrNull() ?: 1
            val m = (numMatcher.group(2)?.toIntOrNull() ?: 1) - 1 // 0-based month
            var y = numMatcher.group(3)?.toIntOrNull() ?: 2026
            if (y < 100) y += 2000

            val cal = Calendar.getInstance()
            cal.set(y, m.coerceIn(0, 11), d.coerceIn(1, 31), 12, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val ts = cal.timeInMillis
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            val matchedEntire = numMatcher.group(0) ?: ""
            val cleaned = working.replace(matchedEntire, " ").trim()
            return DateExtractionResult(ts, dateStr, cleaned)
        }

        // Default to current date
        val now = System.currentTimeMillis()
        val defaultDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        return DateExtractionResult(now, defaultDateStr, working)
    }

    private fun parseMonthNameToIndex(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.startsWith("jan") -> 0
            lower.startsWith("feb") -> 1
            lower.startsWith("mar") -> 2
            lower.startsWith("apr") -> 3
            lower.startsWith("may") -> 4
            lower.startsWith("jun") -> 5
            lower.startsWith("jul") -> 6
            lower.startsWith("aug") -> 7
            lower.startsWith("sep") -> 8
            lower.startsWith("oct") -> 9
            lower.startsWith("nov") -> 10
            lower.startsWith("dec") -> 11
            else -> 0
        }
    }

    private fun extractAmountFromText(text: String): Pair<Double, String> {
        // Try explicit first (e.g. ₹120, Rs 144, 144.00)
        val explicitMatcher = explicitAmountRegex.matcher(text)
        if (explicitMatcher.find()) {
            val match = explicitMatcher.group(1)?.replace(",", "")
            if (match != null) {
                val numVal = parseNumberToken(match)
                if (numVal > 0.0) {
                    val cleaned = text.replace(explicitMatcher.group(0) ?: "", " ")
                    return Pair(numVal, cleaned)
                }
            }
        }

        val matcher = amountRegex.matcher(text)
        while (matcher.find()) {
            val match = matcher.group(1)?.replace(",", "") ?: continue
            val numVal = parseNumberToken(match)
            if (numVal > 0.0) {
                val cleaned = text.replace(matcher.group(0) ?: "", " ")
                return Pair(numVal, cleaned)
            }
        }

        // Generic fallback search for any number
        val simpleNum = Pattern.compile("""\b\d+(?:\.\d{1,2})?\b""").matcher(text)
        if (simpleNum.find()) {
            val numVal = simpleNum.group().toDoubleOrNull() ?: 0.0
            val cleaned = text.replace(simpleNum.group(), " ")
            return Pair(numVal, cleaned)
        }

        return Pair(0.0, text)
    }

    private fun parseNumberToken(match: String): Double {
        return if (match.endsWith("k", ignoreCase = true)) {
            (match.dropLast(1).trim().toDoubleOrNull() ?: 0.0) * 1000
        } else if (match.endsWith("lakh", ignoreCase = true) || match.endsWith("lac", ignoreCase = true)) {
            (match.replace(Regex("(lakh|lac)", RegexOption.IGNORE_CASE), "").trim().toDoubleOrNull() ?: 0.0) * 100000
        } else {
            match.toDoubleOrNull() ?: 0.0
        }
    }

    private fun detectExpenseCategory(text: String): String {
        return when {
            // Fuel & Transport
            text.contains("petrol") || text.contains("diesel") || text.contains("fuel") ||
                    text.contains("uber") || text.contains("ola") || text.contains("cab") ||
                    text.contains("auto") || text.contains("metro") || text.contains("bus") ||
                    text.contains("toll") || text.contains("parking") || text.contains("rapido") ||
                    text.contains("flight") || text.contains("train") -> "Transport & Fuel"

            // Groceries (including fruit, fruite, sabzi, etc.)
            text.contains("grocery") || text.contains("groceries") || text.contains("vegetable") ||
                    text.contains("fruit") || text.contains("fruite") || text.contains("milk") ||
                    text.contains("doodh") || text.contains("sabzi") || text.contains("supermarket") ||
                    text.contains("blinkit") || text.contains("zepto") || text.contains("bigbasket") ||
                    text.contains("d-mart") || text.contains("dmart") || text.contains("ration") -> "Groceries"

            // Food & Dining
            text.contains("food") || text.contains("dining") || text.contains("lunch") ||
                    text.contains("dinner") || text.contains("breakfast") || text.contains("swiggy") ||
                    text.contains("zomato") || text.contains("restaurant") || text.contains("pizza") ||
                    text.contains("burger") || text.contains("cafe") || text.contains("coffee") ||
                    text.contains("tea") || text.contains("chai") || text.contains("biryani") ||
                    text.contains("snack") || text.contains("nashta") || text.contains("sweets") -> "Food & Dining"

            // Utilities & Bills
            text.contains("bill") || text.contains("electricity") || text.contains("bijli") ||
                    text.contains("water") || text.contains("paani") || text.contains("gas") ||
                    text.contains("wifi") || text.contains("recharge") || text.contains("broadband") ||
                    text.contains("mobile") || text.contains("cylinder") -> "Utilities & Bills"

            // Housing & Rent
            text.contains("rent") || text.contains("kiraya") || text.contains("maintenance") ||
                    text.contains("flat") || text.contains("society") || text.contains("house") -> "Housing & Rent"

            // Shopping
            text.contains("shop") || text.contains("amazon") || text.contains("flipkart") ||
                    text.contains("clothes") || text.contains("shoes") || text.contains("myntra") ||
                    text.contains("zara") || text.contains("gadget") || text.contains("electronics") -> "Shopping"

            // Entertainment
            text.contains("movie") || text.contains("netflix") || text.contains("cinema") ||
                    text.contains("game") || text.contains("concert") || text.contains("outing") ||
                    text.contains("party") || text.contains("hotstar") || text.contains("prime") -> "Entertainment"

            // Health & Medical
            text.contains("medicine") || text.contains("dawa") || text.contains("doctor") ||
                    text.contains("hospital") || text.contains("clinic") || text.contains("pharmacy") ||
                    text.contains("tablet") || text.contains("health") || text.contains("test") -> "Healthcare & Meds"

            // Education
            text.contains("school") || text.contains("college") || text.contains("fees") ||
                    text.contains("course") || text.contains("books") || text.contains("tuition") -> "Education"

            // Personal Care
            text.contains("salon") || text.contains("spa") || text.contains("haircut") ||
                    text.contains("gym") || text.contains("fitness") -> "Personal Care"

            // Gifts
            text.contains("gift") || text.contains("donation") || text.contains("charity") ||
                    text.contains("shagun") -> "Gifts & Donations"

            else -> "Other Expense"
        }
    }

    private fun detectIncomeCategory(text: String): String {
        return when {
            text.contains("salary") || text.contains("paycheck") || text.contains("wage") || text.contains("payroll") -> "Salary"
            text.contains("freelance") || text.contains("client") || text.contains("project") || text.contains("business") || text.contains("contract") -> "Business/Freelance"
            text.contains("stock") || text.contains("mutual fund") || text.contains("dividend") || text.contains("crypto") || text.contains("trading") || text.contains("shares") -> "Investments"
            text.contains("rent received") || text.contains("tenant") || text.contains("property") -> "Rental Income"
            text.contains("cashback") || text.contains("reward") || text.contains("scratch card") -> "Cashback & Rewards"
            text.contains("gift") || text.contains("shagun") || text.contains("present") -> "Gifts Received"
            text.contains("refund") || text.contains("reversal") || text.contains("returned") -> "Refunds"
            else -> "Other Income"
        }
    }

    private fun generateCleanTitle(text: String, category: String, type: TransactionType): String {
        // Strip common fluff words, prepositions, slashes, and symbols
        var cleaned = text
            .replace(Regex("""(?i)\b(i\s+)?(paid|spent|gave|bought|purchased|received|got|credited|debited|for|to|on|at|via|by|with|using|rs|inr|rupees|ka|ke|liye|me|se|ko|diya|kiya)\b"""), " ")
            .replace(Regex("""[//\\#₹,;:\-_+=\(\)\[\]]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isBlank() || cleaned.length < 2) {
            return if (type == TransactionType.INCOME) "$category Credit" else "$category Expense"
        }

        // Normalize common misspellings or singulars
        if (cleaned.equals("fruite", ignoreCase = true) || cleaned.equals("fruits", ignoreCase = true)) {
            cleaned = "Fruit"
        }

        return cleaned.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
