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
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiAiService"

data class ExtractionResult(
    val transactions: List<StatementItem>,
    val conversationalSummary: String,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

data class DocumentExtractionResult(
    val documentType: String = "unknown",
    val currency: String = "INR",
    val openingBalance: Double? = null,
    val closingBalance: Double? = null,
    val reportedTotalExpenses: Double? = null,
    val reportedTotalIncome: Double? = null,
    val transactions: List<StatementItem> = emptyList(),
    val documentWarnings: List<String> = emptyList(),
    val rawJson: String = "",
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

object GeminiAiService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // Standard single-transaction schema
    private val transactionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Merchant or brief title of transaction"),
                "amount" to Schema.double(description = "Transaction monetary amount as positive number"),
                "type" to Schema.string(description = "Either EXPENSE or INCOME"),
                "category" to Schema.string(description = "Category name"),
                "paymentMode" to Schema.string(description = "Payment mode: UPI, Cash, Credit Card, Debit Card, Net Banking, Wallet, Other"),
                "date" to Schema.string(description = "Date string in YYYY-MM-DD format if present", nullable = true),
                "note" to Schema.string(description = "Additional transaction note or remarks", nullable = true)
            ),
            optionalProperties = listOf("date", "note")
        )
    }

    private val chatExtractionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "transactions" to Schema.array(
                    items = transactionSchema,
                    description = "List of financial transactions detected from the input"
                ),
                "analysisSummary" to Schema.string(
                    description = "Brief conversational explanation of detected transactions or answer to user"
                )
            ),
            optionalProperties = listOf("analysisSummary")
        )
    }

    // Comprehensive financial document understanding schema
    private val documentExtractionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "document_type" to Schema.string(
                    description = "Type of document: bank_statement, credit_card_statement, receipt, invoice, expense_report, or unknown"
                ),
                "currency" to Schema.string(
                    description = "Detected currency code or symbol, e.g. INR or ₹ (default INR)"
                ),
                "opening_balance" to Schema.double(
                    description = "Starting, opening, or brought-forward balance if stated on document",
                    nullable = true
                ),
                "closing_balance" to Schema.double(
                    description = "Ending balance, closing balance, or money left over if stated on document",
                    nullable = true
                ),
                "reported_total_expenses" to Schema.double(
                    description = "Explicitly stated total expenses, total debits, or money out figure",
                    nullable = true
                ),
                "reported_total_income" to Schema.double(
                    description = "Explicitly stated total income, total credits, or money in figure if stated",
                    nullable = true
                ),
                "transactions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "transaction_date" to Schema.string(
                                description = "Normalized ISO-8601 date string in YYYY-MM-DD format (e.g. 2026-09-14)"
                            ),
                            "description" to Schema.string(
                                description = "Cleaned merchant name or transaction particular (e.g. 'Swiggy', 'Shell Fuel')"
                            ),
                            "amount" to Schema.double(
                                description = "Positive monetary value of the single transaction (e.g. 450.0)"
                            ),
                            "transaction_type" to Schema.string(
                                description = "Either 'debit' for expense or 'credit' for income or refund"
                            ),
                            "category" to Schema.string(
                                description = "Expense/Income category: Food & Dining, Groceries, Transport & Fuel, Shopping, Housing & Rent, Utilities & Bills, Entertainment, Healthcare & Meds, Education, Personal Care, Gifts & Donations, Other Expense, Salary, Business/Freelance, Investments, Rental Income, Cashback & Rewards, Gifts Received, Refunds, Other Income"
                            ),
                            "payment_method" to Schema.string(
                                description = "Payment method: UPI, Cash, Credit Card, Debit Card, Net Banking, Wallet, Other"
                            ),
                            "source_page" to Schema.integer(
                                description = "Document page number (1-indexed) where transaction is located",
                                nullable = true
                            ),
                            "confidence" to Schema.double(
                                description = "Confidence level between 0.0 and 1.0",
                                nullable = true
                            ),
                            "needs_review" to Schema.boolean(
                                description = "True if unclear, ambiguous, or inferred; false if confident",
                                nullable = true
                            )
                        ),
                        optionalProperties = listOf("source_page", "confidence", "needs_review")
                    ),
                    description = "Array of granular, individual debit and credit transactions. NEVER put starting balance, ending balance, or total spending rows here!"
                ),
                "document_warnings" to Schema.array(
                    items = Schema.string(),
                    description = "List of warnings, unparsed rows, low-confidence entries, or discrepancies"
                )
            ),
            optionalProperties = listOf(
                "opening_balance",
                "closing_balance",
                "reported_total_expenses",
                "reported_total_income",
                "document_warnings"
            )
        )
    }

    private val structuredModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = chatExtractionSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    private val documentModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = documentExtractionSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    /**
     * Resolves the active Gemini API key from environment, BuildConfig, or defaults.
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
     * Multimodal extraction directly from document bytes (PDF or Image).
     * Sends original bytes with exact MIME type (application/pdf, image/jpeg, image/png).
     */
    suspend fun extractDocument(
        bytes: ByteArray,
        mimeType: String,
        promptHint: String? = null
    ): DocumentExtractionResult = withContext(Dispatchers.IO) {
        val prompt = buildDocumentExtractionPrompt(promptHint)

        // Tier 1: Firebase AI Logic SDK with gemini-3.8-flash
        try {
            val payload = com.google.firebase.ai.type.Content(
                role = "user",
                parts = listOf(
                    com.google.firebase.ai.type.InlineDataPart(bytes, mimeType),
                    com.google.firebase.ai.type.TextPart(prompt)
                )
            )
            val response = withTimeout(60_000L) {
                documentModel.generateContent(payload)
            }
            val jsonText = response.text
            if (!jsonText.isNullOrBlank()) {
                val parsed = parseJsonToDocumentResult(jsonText)
                if (parsed.isSuccess && (parsed.transactions.isNotEmpty() || parsed.reportedTotalExpenses != null)) {
                    return@withContext parsed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic document extraction failed, trying REST fallback: ${e.message}")
        }

        // Tier 2: Direct Gemini REST API fallback with inlineData
        val apiKey = getActiveApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val restResult = callGeminiRestApiDocument(
                    prompt = prompt,
                    apiKey = apiKey,
                    base64Data = base64,
                    mimeType = mimeType
                )
                if (restResult != null) {
                    val parsed = parseJsonToDocumentResult(restResult)
                    if (parsed.isSuccess && (parsed.transactions.isNotEmpty() || parsed.reportedTotalExpenses != null)) {
                        return@withContext parsed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini REST API document fallback failed: ${e.message}")
            }
        }

        DocumentExtractionResult(
            isSuccess = false,
            errorMessage = "AI document processing could not identify transactions."
        )
    }

    /**
     * Multimodal extraction from rendered page Bitmaps (for multi-page rendered documents or scanned receipts).
     */
    suspend fun extractDocumentFromBitmaps(
        bitmaps: List<Bitmap>,
        promptHint: String? = null
    ): DocumentExtractionResult = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) {
            return@withContext DocumentExtractionResult(isSuccess = false, errorMessage = "No page images provided")
        }
        val prompt = buildDocumentExtractionPrompt(promptHint)

        // Tier 1: Firebase AI Logic SDK
        try {
            val parts = mutableListOf<com.google.firebase.ai.type.Part>()
            bitmaps.forEach { parts.add(com.google.firebase.ai.type.ImagePart(it)) }
            parts.add(com.google.firebase.ai.type.TextPart(prompt))
            val payload = com.google.firebase.ai.type.Content(role = "user", parts = parts)

            val response = withTimeout(60_000L) {
                documentModel.generateContent(payload)
            }
            val jsonText = response.text
            if (!jsonText.isNullOrBlank()) {
                val parsed = parseJsonToDocumentResult(jsonText)
                if (parsed.isSuccess && (parsed.transactions.isNotEmpty() || parsed.reportedTotalExpenses != null)) {
                    return@withContext parsed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic multi-page bitmap extraction failed: ${e.message}")
        }

        // Tier 2: REST fallback with the first page bitmap
        val apiKey = getActiveApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val base64 = bitmapToBase64(bitmaps[0])
                val restResult = callGeminiRestApiDocument(
                    prompt = prompt,
                    apiKey = apiKey,
                    base64Data = base64,
                    mimeType = "image/jpeg"
                )
                if (restResult != null) {
                    val parsed = parseJsonToDocumentResult(restResult)
                    if (parsed.isSuccess) {
                        return@withContext parsed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "REST bitmap fallback failed: ${e.message}")
            }
        }

        DocumentExtractionResult(
            isSuccess = false,
            errorMessage = "Could not extract transactions from page images"
        )
    }

    /**
     * Extracts structured transactions from natural language text, bank SMS messages,
     * or pasted statement rows using Gemini AI.
     */
    suspend fun extractFromText(rawText: String): ExtractionResult = withContext(Dispatchers.IO) {
        val prompt = buildTextExtractionPrompt(rawText)

        // Tier 1: Try Firebase AI Logic SDK with gemini-3.8-flash
        try {
            val response = withTimeout(45_000L) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text
            if (!jsonText.isNullOrBlank()) {
                val parsed = parseJsonToExtractionResult(jsonText, rawText)
                if (parsed.transactions.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic text extraction failed, trying REST API fallback: ${e.message}")
        }

        // Tier 2: Try Direct Gemini REST API
        val apiKey = getActiveApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val restResult = callGeminiRestApi(prompt = prompt, apiKey = apiKey, base64Image = null)
                if (restResult != null) {
                    val parsed = parseJsonToExtractionResult(restResult, rawText)
                    if (parsed.transactions.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini REST API text extraction failed: ${e.message}")
            }
        }

        // Tier 3: Local Smart NLP / Regex Parser
        fallbackTextExtraction(rawText, "Processed with on-device NLP parser")
    }

    /**
     * Multimodal extraction from single image bitmap.
     */
    suspend fun extractFromImage(bitmap: Bitmap, promptHint: String? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val docResult = extractDocumentFromBitmaps(listOf(bitmap), promptHint)
        if (docResult.isSuccess && docResult.transactions.isNotEmpty()) {
            return@withContext ExtractionResult(
                transactions = docResult.transactions,
                conversationalSummary = "Cash Trace AI identified ${docResult.transactions.size} transactions totaling ${CurrencyFormatter.formatCurrency(docResult.transactions.sumOf { it.amount })}.",
                isSuccess = true
            )
        }

        ExtractionResult(
            transactions = emptyList(),
            conversationalSummary = "Unable to detect legible transaction details in the image. Please ensure good lighting and clear text.",
            isSuccess = false,
            errorMessage = docResult.errorMessage ?: "No transactions found"
        )
    }

    /**
     * Processes conversational AI messages and ledger queries.
     */
    suspend fun processChatMessage(
        userMessage: String,
        ledgerContext: String,
        attachedBitmap: Bitmap? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        if (attachedBitmap != null) {
            val imgResult = extractFromImage(attachedBitmap, userMessage.ifBlank { "Extract all transaction rows" })
            return@withContext imgResult
        }

        val prompt = """
            You are Cash Trace AI, an intelligent, helpful personal finance assistant and bank statement parser for Indian Rupees (₹) and global currencies.
            User monthly ledger status:
            $ledgerContext

            User Input:
            "$userMessage"

            CRITICAL PARSING AND EXTRACTION RULES:
            1. MULTIPLE TRANSACTIONS IN SINGLE MESSAGE:
               - If the input contains MULTIPLE transactions (e.g. separated by new lines, commas, semicolons, or conjunctions like "144 fuel on 9 sep 26\n250 groceries on 10 sep 26\n50 tea"):
                 YOU MUST OUTPUT EACH TRANSACTION AS A SEPARATE OBJECT IN THE "transactions" ARRAY.
                 NEVER combine or merge descriptions of different transactions!
                 NEVER drop any amounts!
            2. INDIAN DATE UNDERSTANDING & MAPPING:
               - "144 fuel on 9 sep 26" -> amount: 144.0, title: "Fuel", category: "Transport & Fuel", date: "2026-09-09"
               - "9 sep 26, 144 on fuel" -> amount: 144.0, title: "Fuel", category: "Transport & Fuel", date: "2026-09-09"
               - "9/9/26, 144 on fuel" -> amount: 144.0, title: "Fuel", category: "Transport & Fuel", date: "2026-09-09"
               - "2/9/26 fruite ₹120" -> amount: 120.0, title: "Fruit", category: "Groceries", date: "2026-09-02"
               - In DD/MM/YY format (standard in India), 2/9/26 means 2nd September 2026 (2026-09-02).
               - 2-digit years like 26 represent 2026.
               - Hinglish expressions: "144 ka fuel on 9 sep", "500 doodh wale ko diya", "200 me recharge kiya" must be accurately extracted.
            3. FOR FINANCIAL QUESTIONS:
               - If the user asks a conversational or budget question without logging transactions (e.g. "How much did I spend?", "Can I afford dinner?"):
                 Set "transactions" to empty [].
                 Provide a friendly, encouraging answer in "analysisSummary".
            4. SUMMARY:
               - In analysisSummary, summarize what was found (e.g. "Identified 3 transactions totaling ₹444. Tap Confirm to record them into your ledger.").
        """.trimIndent()

        // Tier 1: Firebase AI Logic
        try {
            val response = withTimeout(45_000L) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text
            if (!jsonText.isNullOrBlank()) {
                val parsed = parseJsonToExtractionResult(jsonText, userMessage)
                if (parsed.conversationalSummary.isNotBlank() || parsed.transactions.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic chat process failed, trying REST API: ${e.message}")
        }

        // Tier 2: Gemini REST API
        val apiKey = getActiveApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val restResult = callGeminiRestApi(prompt = prompt, apiKey = apiKey, base64Image = null)
                if (restResult != null) {
                    val parsed = parseJsonToExtractionResult(restResult, userMessage)
                    if (parsed.conversationalSummary.isNotBlank() || parsed.transactions.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini REST chat processing failed: ${e.message}")
            }
        }

        // Tier 3: Local Fallback
        fallbackTextExtraction(userMessage, "Local NLP Parser")
    }

    private fun buildDocumentExtractionPrompt(promptHint: String?): String {
        return """
            You are Cash Trace AI, an expert bank statement, invoice, receipt, and financial report document parser.
            Your task is to accurately extract all financial details from this document into structured JSON.

            CRITICAL FINANCIAL INTEGRITY RULES:
            1. STRICTLY DISTINGUISH BALANCES & TOTALS FROM TRANSACTIONS:
               - NEVER treat starting balances, opening balances, closing balances, available balances, credit limits, minimum payments due, total expenses, or summary totals as individual transactions.
               - If the document contains "Starting balance: ₹138,693", record: "opening_balance": 138693.0.
               - If the document contains "Total expenses: ₹17,241" or "Total spending: ₹17,241", record: "reported_total_expenses": 17241.0.
               - If the document contains "Money left over: ₹121,452" or "Closing balance: ₹121,452", record: "closing_balance": 121452.0.
               - DO NOT create transaction rows for ₹138,693 or ₹121,452!
               - Only create transaction rows for the actual itemized debit/credit entries whose sum matches the reported total expenses ₹17,241.
            2. MULTI-PAGE & TABLE COMPLETENESS:
               - Extract every single transaction across all pages, tables, and rows without omitting any rows.
               - Normalize date to ISO-8601 YYYY-MM-DD.
               - Ensure transaction amounts are positive numbers.
               - Set transaction_type to 'debit' for expenses/withdrawals/purchases and 'credit' for income/deposits/refunds/salary.
            3. AMBIGUOUS ROWS:
               - If any row is ambiguous or confidence < 0.8, set needs_review: true and explain in document_warnings.
            ${promptHint?.let { "Additional context: $it" } ?: ""}
        """.trimIndent()
    }

    private fun buildTextExtractionPrompt(rawText: String): String {
        return """
            You are Cash Trace AI, an expert bank statement and personal finance parser.
            Extract all financial transactions from the following text (which may be a bank SMS, credit card statement, UPI notice, or spreadsheet CSV):
            For each transaction extract:
            - title: merchant or description (e.g. 'Swiggy', 'D-Mart', 'Salary')
            - amount: positive numeric value (e.g. 350.0)
            - type: EXPENSE or INCOME (salary, refund, received money is INCOME; purchases, bills, debits are EXPENSE)
            - category: choose the best fitting category
            - paymentMode: choose from [UPI, Cash, Credit Card, Debit Card, Net Banking, Wallet, Other]
            - date: in YYYY-MM-DD format if mentioned
            - note: reference number, VPA, or remarks

            Text to analyze:
            $rawText
        """.trimIndent()
    }

    /**
     * Direct Gemini REST API caller for financial document payloads (PDF or image).
     */
    private fun callGeminiRestApiDocument(
        prompt: String,
        apiKey: String,
        base64Data: String?,
        mimeType: String
    ): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val root = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()

        if (!base64Data.isNullOrBlank()) {
            val dataPart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", mimeType)
            inlineData.put("data", base64Data)
            dataPart.put("inlineData", inlineData)
            partsArray.put(dataPart)
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
            Log.w(TAG, "Gemini REST document API HTTP error: ${response.code} ${response.message}")
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

    private fun callGeminiRestApi(prompt: String, apiKey: String, base64Image: String?): String? {
        return callGeminiRestApiDocument(
            prompt = prompt,
            apiKey = apiKey,
            base64Data = base64Image,
            mimeType = "image/jpeg"
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

    fun parseJsonToDocumentResult(jsonText: String): DocumentExtractionResult {
        return try {
            val cleanJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val root = JSONObject(cleanJson)
            val docType = root.optString("document_type", "bank_statement")
            val currency = root.optString("currency", "INR")

            val openBal = if (root.has("opening_balance") && !root.isNull("opening_balance")) {
                root.optDouble("opening_balance")
            } else null

            val closeBal = if (root.has("closing_balance") && !root.isNull("closing_balance")) {
                root.optDouble("closing_balance")
            } else null

            val repExpenses = if (root.has("reported_total_expenses") && !root.isNull("reported_total_expenses")) {
                root.optDouble("reported_total_expenses")
            } else null

            val repIncome = if (root.has("reported_total_income") && !root.isNull("reported_total_income")) {
                root.optDouble("reported_total_income")
            } else null

            val warningsList = mutableListOf<String>()
            val warnArray = root.optJSONArray("document_warnings")
            if (warnArray != null) {
                for (w in 0 until warnArray.length()) {
                    val msg = warnArray.optString(w)
                    if (msg.isNotBlank()) warningsList.add(msg)
                }
            }

            val txArray = root.optJSONArray("transactions")
            val items = mutableListOf<StatementItem>()

            if (txArray != null) {
                for (i in 0 until txArray.length()) {
                    val obj = txArray.getJSONObject(i)
                    val rawTitle = obj.optString("description", "").ifBlank {
                        obj.optString("title", "Transaction ${i + 1}")
                    }.trim()

                    val rawAmount = Math.abs(obj.optDouble("amount", 0.0))
                    if (rawAmount <= 0.0 && rawTitle.isBlank()) continue

                    // Guard: filter out any balance summary row that accidentally leaked into transactions array
                    val lowerTitle = rawTitle.lowercase()
                    val isBalanceRow = lowerTitle.contains("starting balance") ||
                            lowerTitle.contains("opening balance") ||
                            lowerTitle.contains("balance b/f") ||
                            lowerTitle.contains("closing balance") ||
                            lowerTitle.contains("money left over") ||
                            lowerTitle.contains("total expenses") ||
                            lowerTitle.contains("total spending") ||
                            lowerTitle.contains("summary")

                    if (isBalanceRow && (
                                (openBal != null && Math.abs(rawAmount - openBal) < 1.0) ||
                                (closeBal != null && Math.abs(rawAmount - closeBal) < 1.0) ||
                                (repExpenses != null && Math.abs(rawAmount - repExpenses) < 1.0)
                            )
                    ) {
                        warningsList.add("Filtered balance summary row from transactions: '$rawTitle' (₹$rawAmount)")
                        continue
                    }

                    val rawType = obj.optString("transaction_type", obj.optString("type", "debit")).lowercase()
                    val type = if (rawType == "credit" || rawType == "income") TransactionType.INCOME else TransactionType.EXPENSE

                    val categoryStr = obj.optString("category", "Other Expense")
                    val category = sanitizeCategory(categoryStr, type)

                    val paymentModeStr = obj.optString("payment_method", obj.optString("paymentMode", "UPI"))
                    val paymentMode = sanitizePaymentMode(paymentModeStr)

                    val dateStr = obj.optString("transaction_date", obj.optString("date", ""))
                    val timestamp = parseDateStringToTimestamp(dateStr)

                    val pageNum = obj.optInt("source_page", 1)
                    val confidence = obj.optDouble("confidence", 0.95).toFloat()
                    val needsReview = obj.optBoolean("needs_review", false) || confidence < 0.75f

                    val fp = computeTransactionFingerprint(
                        dateStr = dateStr,
                        title = rawTitle,
                        amount = rawAmount,
                        type = type
                    )

                    items.add(
                        StatementItem(
                            id = UUID.randomUUID().toString(),
                            fingerprint = fp,
                            title = rawTitle,
                            amount = rawAmount,
                            type = type,
                            category = category,
                            paymentMode = paymentMode,
                            timestamp = timestamp,
                            sourcePage = pageNum,
                            confidence = confidence,
                            needsReview = needsReview,
                            isDuplicate = false,
                            isSelected = true,
                            note = "Page $pageNum • Confidence: ${(confidence * 100).toInt()}%",
                            originalText = rawTitle
                        )
                    )
                }
            }

            DocumentExtractionResult(
                documentType = docType,
                currency = currency,
                openingBalance = openBal,
                closingBalance = closeBal,
                reportedTotalExpenses = repExpenses,
                reportedTotalIncome = repIncome,
                transactions = items,
                documentWarnings = warningsList,
                rawJson = cleanJson,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document extraction JSON: ${e.message}", e)
            DocumentExtractionResult(
                isSuccess = false,
                errorMessage = "Failed to parse document JSON: ${e.localizedMessage}"
            )
        }
    }

    private fun parseJsonToExtractionResult(jsonText: String, originalText: String): ExtractionResult {
        return try {
            val cleanJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val root = JSONObject(cleanJson)
            val summary = root.optString("analysisSummary", "")
            val txArray = root.optJSONArray("transactions")

            val items = mutableListOf<StatementItem>()
            if (txArray != null) {
                for (i in 0 until txArray.length()) {
                    val obj = txArray.getJSONObject(i)
                    val rawTitle = obj.optString("title", "Expense").trim()
                    val amount = Math.abs(obj.optDouble("amount", 0.0))
                    if (amount <= 0.0 && rawTitle.isBlank()) continue

                    val typeStr = obj.optString("type", "EXPENSE").uppercase()
                    val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE

                    val categoryStr = obj.optString("category", "Other Expense")
                    val category = sanitizeCategory(categoryStr, type)

                    val modeStr = obj.optString("paymentMode", "UPI")
                    val paymentMode = sanitizePaymentMode(modeStr)

                    val dateStr = obj.optString("date", "")
                    val timestamp = parseDateStringToTimestamp(dateStr)
                    val note = obj.optString("note", "")

                    val fp = computeTransactionFingerprint(dateStr, rawTitle, amount, type)

                    items.add(
                        StatementItem(
                            id = UUID.randomUUID().toString(),
                            fingerprint = fp,
                            title = if (rawTitle.isBlank()) category else rawTitle,
                            amount = amount,
                            type = type,
                            category = category,
                            paymentMode = paymentMode,
                            timestamp = timestamp,
                            sourcePage = 1,
                            confidence = 0.95f,
                            needsReview = false,
                            isDuplicate = false,
                            note = note,
                            originalText = originalText.take(120),
                            isSelected = true
                        )
                    )
                }
            }

            val finalSummary = if (summary.isNotBlank()) {
                summary
            } else if (items.isNotEmpty()) {
                val totalAmount = items.sumOf { it.amount }
                "Cash Trace AI identified ${items.size} transaction${if (items.size > 1) "s" else ""} totaling ${CurrencyFormatter.formatCurrency(totalAmount)}."
            } else {
                "No transactions identified."
            }

            ExtractionResult(
                transactions = items,
                conversationalSummary = finalSummary,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON from Gemini: ${e.message}", e)
            fallbackTextExtraction(originalText, "JSON parse error")
        }
    }

    private fun fallbackTextExtraction(text: String, reason: String): ExtractionResult {
        val parsedList = ExpenseParser.parseMultipleExpenses(text, sourceTag = "CHAT_FALLBACK")
        if (parsedList.isEmpty()) {
            return ExtractionResult(
                transactions = emptyList(),
                conversationalSummary = "I couldn't identify any transactions in your message. You can enter something like '144 fuel on 9 sep 26' or '50 tea'.",
                isSuccess = false,
                errorMessage = reason
            )
        }

        val items = parsedList.map { parsed ->
            val fp = computeTransactionFingerprint(parsed.dateString, parsed.title, parsed.amount, parsed.type)
            StatementItem(
                id = UUID.randomUUID().toString(),
                fingerprint = fp,
                title = parsed.title,
                amount = parsed.amount,
                type = parsed.type,
                category = parsed.category,
                paymentMode = parsed.paymentMode,
                timestamp = if (parsed.timestamp > 0L) parsed.timestamp else System.currentTimeMillis(),
                sourcePage = 1,
                confidence = parsed.confidence,
                needsReview = false,
                isDuplicate = false,
                note = "Parsed ($reason)",
                originalText = parsed.rawText.take(120),
                isSelected = true
            )
        }

        val totalDebits = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalCredits = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

        val summary = if (items.size == 1) {
            val itm = items[0]
            val typeLabel = if (itm.type == TransactionType.INCOME) "Income credit" else "Expense debit"
            "Detected $typeLabel of ${CurrencyFormatter.formatCurrency(itm.amount)} for \"${itm.title}\" under ${itm.category} via ${itm.paymentMode.displayName}."
        } else {
            "Detected ${items.size} transactions (${items.count { it.type == TransactionType.EXPENSE }} expenses totaling ${CurrencyFormatter.formatCurrency(totalDebits)})."
        }

        return ExtractionResult(
            transactions = items,
            conversationalSummary = summary,
            isSuccess = true,
            errorMessage = reason
        )
    }

    fun computeTransactionFingerprint(
        dateStr: String,
        title: String,
        amount: Double,
        type: TransactionType
    ): String {
        val normTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val amtStr = String.format(Locale.US, "%.2f", amount)
        val raw = "$dateStr|$normTitle|$amtStr|${type.name}"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            digest.take(8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            raw.hashCode().toString()
        }
    }

    fun sanitizeCategory(rawCategory: String, type: TransactionType): String {
        if (rawCategory.isBlank()) return if (type == TransactionType.INCOME) "Salary" else "Other Expense"
        val trimmed = rawCategory.trim()
        val list = if (type == TransactionType.INCOME) Categories.incomeCategories else Categories.expenseCategories

        // 1. Exact match
        list.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.name }

        // 2. Contains match either way
        list.firstOrNull { trimmed.contains(it.name, ignoreCase = true) || it.name.contains(trimmed, ignoreCase = true) }?.let { return it.name }

        // 3. Keyword heuristic
        val lower = trimmed.lowercase()
        if (type == TransactionType.EXPENSE) {
            when {
                lower.contains("food") || lower.contains("dining") || lower.contains("eat") || lower.contains("restaurant") || lower.contains("cafe") || lower.contains("snack") -> return "Food & Dining"
                lower.contains("groc") || lower.contains("vegetable") || lower.contains("fruit") || lower.contains("supermarket") || lower.contains("market") -> return "Groceries"
                lower.contains("transit") || lower.contains("transport") || lower.contains("travel") || lower.contains("fuel") || lower.contains("petrol") || lower.contains("diesel") || lower.contains("cab") || lower.contains("uber") || lower.contains("ola") -> return "Transport & Fuel"
                lower.contains("bill") || lower.contains("utility") || lower.contains("electricity") || lower.contains("water") || lower.contains("wifi") || lower.contains("recharge") -> return "Utilities & Bills"
                lower.contains("shop") || lower.contains("cloth") || lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") -> return "Shopping"
                lower.contains("rent") || lower.contains("house") || lower.contains("home") -> return "Housing & Rent"
                lower.contains("med") || lower.contains("health") || lower.contains("pharmacy") || lower.contains("hospital") || lower.contains("doctor") -> return "Healthcare & Meds"
                lower.contains("movie") || lower.contains("cinema") || lower.contains("entertainment") || lower.contains("ott") || lower.contains("netflix") || lower.contains("hotstar") -> return "Entertainment"
                lower.contains("school") || lower.contains("college") || lower.contains("course") || lower.contains("tuition") || lower.contains("fee") -> return "Education"
                lower.contains("salon") || lower.contains("spa") || lower.contains("beauty") || lower.contains("grooming") -> return "Personal Care"
                lower.contains("gift") || lower.contains("donation") || lower.contains("charity") -> return "Gifts & Donations"
            }
        } else {
            when {
                lower.contains("salary") || lower.contains("payroll") || lower.contains("wages") -> return "Salary"
                lower.contains("freelance") || lower.contains("client") || lower.contains("consult") || lower.contains("business") -> return "Business/Freelance"
                lower.contains("invest") || lower.contains("dividend") || lower.contains("stock") || lower.contains("mutual") -> return "Investments"
                lower.contains("rent") -> return "Rental Income"
                lower.contains("cashback") || lower.contains("reward") || lower.contains("scratch") -> return "Cashback & Rewards"
                lower.contains("refund") || lower.contains("reversal") -> return "Refunds"
                lower.contains("gift") -> return "Gifts Received"
            }
        }

        // If valid string, return trimmed custom category name
        return if (trimmed.length in 2..30) trimmed else (if (type == TransactionType.INCOME) "Other Income" else "Other Expense")
    }

    fun sanitizePaymentMode(rawMode: String): PaymentMode {
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

    fun parseDateStringToTimestamp(dateStr: String): Long {
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
                    // Normalize 2-digit year interpretation: e.g. 26 -> 2026
                    var y = cal.get(Calendar.YEAR)
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
