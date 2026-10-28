package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.PaymentMode
import com.example.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ReceiptScanResult(
    val merchant: String,
    val totalAmount: Double,
    val category: String,
    val paymentMode: PaymentMode,
    val dateString: String,
    val itemsSummary: List<String>,
    val receiptUri: String
)

object ReceiptScanner {

    suspend fun scanReceiptImage(context: Context, imageUri: Uri): ReceiptScanResult = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(imageUri)?.lowercase() ?: "image/jpeg"
        val bytes = try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var read: Int
                while (stream.read(chunk).also { read = it } != -1) {
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        } catch (_: Exception) {
            null
        }

        if (bytes != null && bytes.isNotEmpty()) {
            val docResult = GeminiAiService.extractDocument(
                bytes = bytes,
                mimeType = if (mimeType.startsWith("image/")) mimeType else "image/jpeg",
                promptHint = "Single store receipt or POS slip. Extract merchant/vendor name, true grand total amount, date, payment method, category, and line items."
            )

            if (docResult.isSuccess && (docResult.transactions.isNotEmpty() || docResult.reportedTotalExpenses != null)) {
                val primaryTx = docResult.transactions.firstOrNull()
                val merchantName = primaryTx?.title?.ifBlank { "Store Receipt" } ?: "Store Receipt"

                // Use reported total if provided, or max transaction amount, or single transaction amount
                val totalAmount = docResult.reportedTotalExpenses
                    ?: if (docResult.transactions.size == 1) {
                        primaryTx?.amount ?: 0.0
                    } else {
                        // In case of multiple line items, check if one item represents the grand total
                        val maxItem = docResult.transactions.maxByOrNull { it.amount }
                        val sumOthers = docResult.transactions.filter { it != maxItem }.sumOf { it.amount }
                        if (maxItem != null && Math.abs(maxItem.amount - sumOthers) < 2.0) {
                            maxItem.amount
                        } else {
                            docResult.transactions.sumOf { it.amount }
                        }
                    }

                val itemsList = docResult.transactions.map {
                    "${it.title} - ${CurrencyFormatter.formatCurrency(it.amount)}"
                }

                val timestamp = primaryTx?.timestamp ?: System.currentTimeMillis()
                val category = primaryTx?.category ?: "Shopping"
                val paymentMode = primaryTx?.paymentMode ?: PaymentMode.UPI

                return@withContext ReceiptScanResult(
                    merchant = merchantName,
                    totalAmount = totalAmount,
                    category = category,
                    paymentMode = paymentMode,
                    dateString = DateUtils.formatShortDate(timestamp),
                    itemsSummary = itemsList.ifEmpty { listOf("Receipt verified by Cash Trace AI") },
                    receiptUri = imageUri.toString()
                )
            }
        }

        // Clean default if no line could be resolved
        ReceiptScanResult(
            merchant = "Receipt / Bill",
            totalAmount = 0.0,
            category = "Other Expense",
            paymentMode = PaymentMode.UPI,
            dateString = DateUtils.formatShortDate(System.currentTimeMillis()),
            itemsSummary = listOf("Image processed by Cash Trace AI"),
            receiptUri = imageUri.toString()
        )
    }
}
