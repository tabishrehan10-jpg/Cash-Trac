package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.Categories
import com.example.data.ExpenseTransaction
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "ReceiptExtractionService"

/**
 * Line item on a receipt or breakdown in a transaction log.
 */
data class ExtractedLineItem(
    val description: String,
    val amount: Double
)

/**
 * Structured details extracted automatically from captured receipt text or transaction logs.
 */
data class ExtractedReceiptDetails(
    val vendor: String,
    val totalAmount: Double,
    val date: String,
    val timestamp: Long,
    val category: String,
    val paymentMode: PaymentMode,
    val type: TransactionType = TransactionType.EXPENSE,
    val currency: String = "INR",
    val lineItems: List<ExtractedLineItem> = emptyList(),
    val rawText: String = "",
    val confidenceNotes: String = "",
    val isMissingVendor: Boolean = false,
    val isMissingAmount: Boolean = false,
    val isMissingDate: Boolean = false
) {
    val hasMissingFields: Boolean
        get() = isMissingVendor || isMissingAmount || isMissingDate || vendor.isBlank() || totalAmount <= 0.0
}

/**
 * State representing receipt or transaction log extraction.
 */
sealed class ReceiptExtractionUiState {
    object Idle : ReceiptExtractionUiState()
    object Loading : ReceiptExtractionUiState()
    data class Success(val details: ExtractedReceiptDetails) : ReceiptExtractionUiState()
    data class Error(val message: String) : ReceiptExtractionUiState()
}

/**
 * Service that sends captured receipt text or transaction logs to the Gemini API
 * to extract key details like vendor, total amount, and date automatically.
 *
 * Uses Firebase AI Logic SDK (gemini-3.8-flash) with structured JSON Schema output,
 * backed by resilient fallbacks.
 */
object ReceiptExtractionService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // Line item schema
    private val lineItemSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "description" to Schema.string(description = "Item name, description, or charge item"),
                "amount" to Schema.double(description = "Price or amount of the item as a positive number")
            )
        )
    }

    // Comprehensive receipt & transaction log extraction schema
    private val extractionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "vendor" to Schema.string(
                    description = "The vendor, merchant, store, shop, service provider, or counterpart name (e.g., Starbucks, Amazon, D-Mart, Swiggy, Shell, Reliance Fresh, HDFC, Uber)"
                ),
                "totalAmount" to Schema.double(
                    description = "The final total monetary amount paid or credited as a positive number (e.g. 420.50)"
                ),
                "date" to Schema.string(
                    description = "The transaction or purchase date in YYYY-MM-DD format (e.g., 2025-05-14) if found or inferred from context"
                ),
                "category" to Schema.string(
                    description = "Best fitting category: Food & Dining, Groceries, Transport & Fuel, Shopping, Housing & Rent, Utilities & Bills, Entertainment, Healthcare & Meds, Education, Personal Care, Gifts & Donations, Other Expense, Salary, Business/Freelance, Investments, Rental Income, Cashback & Rewards, Gifts Received, Refunds, Other Income"
                ),
                "paymentMode" to Schema.string(
                    description = "Detected payment method: UPI, Cash, Credit Card, Debit Card, Net Banking, Wallet, Other"
                ),
                "type" to Schema.string(
                    description = "Either EXPENSE or INCOME. Incomes include refunds, salary credits, cashback. Debits/purchases/bills are EXPENSE."
                ),
                "currency" to Schema.string(
                    description = "Currency detected, such as INR, USD, EUR, or GBP (defaults to INR)"
                ),
                "lineItems" to Schema.array(
                    items = lineItemSchema,
                    description = "Individual bill line items or itemized breakdown if present"
                ),
                "notes" to Schema.string(
                    description = "Brief 1-sentence summary of the receipt or transaction log (e.g., 'Swiggy dinner order paid via UPI')"
                )
            ),
            optionalProperties = listOf("date", "category", "paymentMode", "type", "currency", "lineItems", "notes")
        )
    }

    private val generativeModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = extractionSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    /**
     * Resolves the active Gemini API key from environment, BuildConfig, or defaults for fallback.
     */
    private fun getActiveApiKey(): String? {
        val key = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            null
        }
        if (!key.isNullOrBlank() && key != "MY_GEMINI_API_KEY") {
            return key
        }
        return "AIzaSyCadU465L6fJKpm1NW9wfUTYVo9EZ4EIHk"
    }

    /**
     * Sends captured receipt text (e.g. from OCR scanner, digital invoice, or POS printout)
     * to the Gemini API to extract key details automatically.
     */
    suspend fun extractFromReceiptText(capturedText: String): ExtractedReceiptDetails = withContext(Dispatchers.IO) {
        val prompt = buildReceiptPrompt(capturedText)
        extractWithGemini(prompt = prompt, rawInput = capturedText, isReceipt = true)
    }

    /**
     * Sends transaction logs (e.g. bank SMS notifications, payment app log entries,
     * credit/debit card alerts, or CSV log rows) to the Gemini API to extract key details automatically.
     */
    suspend fun extractFromTransactionLog(transactionLog: String): ExtractedReceiptDetails = withContext(Dispatchers.IO) {
        val prompt = buildTransactionLogPrompt(transactionLog)
        extractWithGemini(prompt = prompt, rawInput = transactionLog, isReceipt = false)
    }

    /**
     * Unified automatic extraction method for either receipt text or transaction logs.
     */
    suspend fun extractAutomatically(input: String): ExtractedReceiptDetails = withContext(Dispatchers.IO) {
        val lower = input.lowercase()
        val isLog = lower.contains("debited") || lower.contains("credited") ||
                lower.contains("a/c") || lower.contains("acct") ||
                lower.contains("vpa") || lower.contains("txn") ||
                lower.contains("ref no") || lower.contains("sms") ||
                lower.contains("alert")

        if (isLog) {
            extractFromTransactionLog(input)
        } else {
            extractFromReceiptText(input)
        }
    }

    private suspend fun extractWithGemini(prompt: String, rawInput: String, isReceipt: Boolean): ExtractedReceiptDetails {
        // Tier 1: Try Firebase AI Logic SDK (gemini-3.8-flash with JSON schema)
        try {
            val response = withTimeout(45_000L) {
                generativeModel.generateContent(prompt)
            }
            val jsonText = response.text
            if (!jsonText.isNullOrBlank()) {
                val parsed = parseJsonResponse(jsonText, rawInput, isReceipt)
                if (parsed.vendor.isNotBlank() && parsed.totalAmount > 0.0) {
                    return parsed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic extraction failed, trying REST API fallback: ${e.message}")
        }

        // Tier 2: Resilient REST API fallback with Gemini
        val apiKey = getActiveApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val restResult = callGeminiRestApi(prompt, apiKey)
                if (restResult != null) {
                    val parsed = parseJsonResponse(restResult, rawInput, isReceipt)
                    if (parsed.vendor.isNotBlank()) {
                        return parsed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini REST API extraction fallback failed: ${e.message}")
            }
        }

        // Tier 3: Deterministic local heuristic/NLP fallback
        return fallbackLocalExtraction(rawInput, isReceipt)
    }

    private fun buildReceiptPrompt(capturedText: String): String {
        return """
            You are Cash Trace AI, an expert receipt and POS slip extractor.
            Analyze the following captured receipt text and accurately extract the key details:
            1. vendor: Name of the merchant, store, shop, restaurant, or business on the receipt.
            2. totalAmount: The final grand total amount charged as a positive number (exclude taxes/tips unless they are part of the total).
            3. date: The date of the receipt in YYYY-MM-DD format (if ambiguous, pick the most plausible transaction date).
            4. category: The best fitting category (e.g. Food & Dining, Groceries, Shopping, Healthcare & Meds).
            5. paymentMode: How it was paid (UPI, Cash, Credit Card, Debit Card, etc.).
            6. lineItems: Itemized list of purchased products or services with individual prices if listed.
            7. notes: A concise summary of the purchase.

            Captured receipt text:
            \"\"\"
            $capturedText
            \"\"\"
        """.trimIndent()
    }

    private fun buildTransactionLogPrompt(transactionLog: String): String {
        return """
            You are Cash Trace AI, an expert financial transaction log and bank SMS analyzer.
            Analyze the following transaction log / notification text and extract the key details:
            1. vendor: Merchant, counterparty name, VPA recipient, or entity (e.g., Amazon, Swiggy, Uber, Netflix, Indian Oil).
            2. totalAmount: Monetary transaction amount as a positive number.
            3. date: Transaction date in YYYY-MM-DD format.
            4. type: EXPENSE for debits/purchases; INCOME for credits, refunds, or salary.
            5. category: Best category (Food & Dining, Transport & Fuel, Shopping, Utilities & Bills, Salary, etc.).
            6. paymentMode: Payment method (UPI, Net Banking, Credit Card, Debit Card, etc.).
            7. notes: Reference number, UPI ID, or context.

            Transaction log:
            \"\"\"
            $transactionLog
            \"\"\"
        """.trimIndent()
    }

    /**
     * Sends a photo/image of a handwritten transaction note, paper receipt, khata book,
     * or daily expense log to Gemini API for multimodal extraction.
     */
    suspend fun extractFromWrittenTransactionImage(bitmap: Bitmap): ExtractedReceiptDetails = withContext(Dispatchers.IO) {
        val prompt = """
            You are Cash Trace AI, an expert at reading handwritten financial notes, paper receipts, daily expense registers, khata entries, cash memos, and printed bills.
            Extract the following transaction details into structured JSON:
            - vendor: merchant, shop, person, or category name (e.g. 'Fruit stall', 'Fuel station', 'Ramesh Dairy', 'Swiggy', 'Auto'). If unclear or not found, leave as empty string "".
            - totalAmount: total amount as a positive number (e.g. 120.0, 144.0). If not clearly legible or missing, return 0.0.
            - date: in YYYY-MM-DD format (if written like 9/9/26 or 9 sep 26, interpret as 2026-09-09).
            - category: best category (Food & Dining, Groceries, Transport & Fuel, Utilities & Bills, Housing & Rent, Shopping, Healthcare & Meds, Education, Personal Care, Entertainment, Salary, etc.)
            - paymentMode: UPI, Cash, Credit Card, Debit Card, Net Banking, Wallet
            - type: EXPENSE or INCOME
            - notes: if any details like quantity, unit price, or description are visible
        """.trimIndent()

        // Tier 1: Try Firebase AI Logic multimodal
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            val inputContent = Content(
                role = "user",
                parts = listOf(
                    InlineDataPart(bytes, "image/jpeg"),
                    TextPart(prompt)
                )
            )
            val response = withTimeout(45_000L) {
                generativeModel.generateContent(inputContent)
            }
            val jsonText = response.text
            if (!jsonText.isNullOrBlank()) {
                val parsed = parseJsonResponse(jsonText, "Handwritten / Written Note Image", isReceipt = true)
                return@withContext parsed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Multimodal written image extraction via Firebase AI Logic failed: ${e.message}")
        }

        // Tier 2: Try REST API with image base64
        val apiKey = getActiveApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val base64Img = bitmapToBase64(bitmap)
                val restResult = callGeminiRestApi(prompt, apiKey, base64Img)
                if (restResult != null) {
                    val parsed = parseJsonResponse(restResult, "Handwritten / Written Note Image", isReceipt = true)
                    return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "REST multimodal failed: ${e.message}")
            }
        }

        // Tier 3: Fallback requiring user completion
        ExtractedReceiptDetails(
            vendor = "",
            totalAmount = 0.0,
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            timestamp = System.currentTimeMillis(),
            category = "Other Expense",
            paymentMode = PaymentMode.CASH,
            type = TransactionType.EXPENSE,
            confidenceNotes = "Image uploaded. Please write the missing details below to complete your transaction.",
            isMissingVendor = true,
            isMissingAmount = true,
            isMissingDate = false
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1200
        val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            Math.max(bitmap.width.toFloat() / maxDim, bitmap.height.toFloat() / maxDim)
        } else 1f
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width / scale).toInt(),
                (bitmap.height / scale).toInt(),
                true
            )
        } else bitmap

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun parseJsonResponse(jsonString: String, rawInput: String, isReceipt: Boolean): ExtractedReceiptDetails {
        val clean = jsonString.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(clean)
        val rawVendor = json.optString("vendor", "").trim()
        val totalAmount = Math.abs(json.optDouble("totalAmount", 0.0))
        val rawDate = json.optString("date", "").trim()
        val rawCategory = json.optString("category", if (isReceipt) "Shopping" else "Other Expense")
        val rawMode = json.optString("paymentMode", "UPI")
        val rawType = json.optString("type", "EXPENSE").uppercase()
        val currency = json.optString("currency", "INR")
        val notes = json.optString("notes", "")

        val type = if (rawType == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
        val category = sanitizeCategory(rawCategory, type)
        val paymentMode = sanitizePaymentMode(rawMode)
        val timestamp = parseDateStringToTimestamp(rawDate)
        val formattedDate = if (rawDate.isNotBlank()) rawDate else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

        val lineItems = mutableListOf<ExtractedLineItem>()
        val itemsArray = json.optJSONArray("lineItems")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val desc = itemObj.optString("description", "Item ${i + 1}").trim()
                val amt = Math.abs(itemObj.optDouble("amount", 0.0))
                if (desc.isNotBlank()) {
                    lineItems.add(ExtractedLineItem(desc, amt))
                }
            }
        }

        val isMissingVendor = rawVendor.isBlank() || rawVendor.equals("Unknown", ignoreCase = true) || rawVendor.equals("Scanned Receipt", ignoreCase = true) || rawVendor.equals("Transaction", ignoreCase = true)
        val isMissingAmount = totalAmount <= 0.0
        val isMissingDate = rawDate.isBlank()

        val vendor = if (rawVendor.isNotBlank()) {
            rawVendor
        } else if (lineItems.isNotEmpty()) {
            lineItems[0].description
        } else {
            if (isReceipt) "Scanned Receipt" else "Transaction"
        }

        return ExtractedReceiptDetails(
            vendor = vendor,
            totalAmount = totalAmount,
            date = formattedDate,
            timestamp = timestamp,
            category = category,
            paymentMode = paymentMode,
            type = type,
            currency = currency,
            lineItems = lineItems,
            rawText = rawInput,
            confidenceNotes = if (notes.isNotBlank()) notes else "Extracted automatically by Gemini AI",
            isMissingVendor = isMissingVendor,
            isMissingAmount = isMissingAmount,
            isMissingDate = isMissingDate
        )
    }

    private fun callGeminiRestApi(prompt: String, apiKey: String, base64Image: String? = null): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val root = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()

        if (!base64Image.isNullOrBlank()) {
            val imagePart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", "image/jpeg")
            inlineData.put("data", base64Image)
            imagePart.put("inlineData", inlineData)
            partsArray.put(imagePart)
        }

        val textPart = JSONObject()
        textPart.put("text", prompt)
        partsArray.put(textPart)

        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        val genConfig = JSONObject()
        genConfig.put("responseMimeType", "application/json")
        root.put("generationConfig", genConfig)

        val body = root.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini REST API error code: ${response.code}")
            return null
        }
        val responseBody = response.body?.string() ?: return null
        val respJson = JSONObject(responseBody)
        val candidates = respJson.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        return parts.getJSONObject(0).optString("text")
    }

    private fun fallbackLocalExtraction(text: String, isReceipt: Boolean): ExtractedReceiptDetails {
        val parsed = ExpenseParser.parseInput(text, sourceTag = if (isReceipt) "RECEIPT_LOCAL" else "LOG_LOCAL")
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

        return ExtractedReceiptDetails(
            vendor = parsed.title,
            totalAmount = parsed.amount,
            date = dateStr,
            timestamp = timestamp,
            category = parsed.category,
            paymentMode = parsed.paymentMode,
            type = parsed.type,
            currency = "INR",
            lineItems = emptyList(),
            rawText = text,
            confidenceNotes = "Extracted using on-device pattern matching (offline fallback)"
        )
    }

    private fun sanitizeCategory(rawCategory: String, type: TransactionType): String {
        val list = if (type == TransactionType.INCOME) Categories.incomeCategories else Categories.expenseCategories
        return list.firstOrNull { it.name.equals(rawCategory, ignoreCase = true) }?.name
            ?: list.firstOrNull { rawCategory.contains(it.name, ignoreCase = true) }?.name
            ?: if (type == TransactionType.INCOME) "Salary" else "Other Expense"
    }

    private fun sanitizePaymentMode(rawMode: String): PaymentMode {
        val lower = rawMode.lowercase()
        return when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> PaymentMode.UPI
            lower.contains("credit") -> PaymentMode.CREDIT_CARD
            lower.contains("debit") -> PaymentMode.DEBIT_CARD
            lower.contains("net") || lower.contains("bank") || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps") -> PaymentMode.NET_BANKING
            lower.contains("wallet") -> PaymentMode.WALLET
            lower.contains("cash") -> PaymentMode.CASH
            else -> PaymentMode.UPI
        }
    }

    private fun parseDateStringToTimestamp(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val cleaned = dateStr.trim().removePrefix("on ").trim()
        val formats = listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd/MM/yy",
            "d/M/yy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "dd-MM-yy",
            "d-M-yy",
            "d-M-yyyy",
            "dd MMM yyyy",
            "d MMM yyyy",
            "dd MMM yy",
            "d MMM yy",
            "dd-MMM-yyyy",
            "d-MMM-yy",
            "dd-MMM-yy",
            "dd MMMM yyyy",
            "d MMMM yyyy",
            "yyyy/MM/dd",
            "dd.MM.yyyy",
            "d.M.yy",
            "MM/dd/yyyy",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(cleaned)
                if (date != null && date.time > 0L) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    val y = cal.get(Calendar.YEAR)
                    if (y < 100) {
                        cal.set(Calendar.YEAR, y + 2000)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}
