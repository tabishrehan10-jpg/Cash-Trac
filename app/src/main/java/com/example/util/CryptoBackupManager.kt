package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.ExpenseTransaction
import com.example.data.MonthBudgetConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoBackupManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    data class BackupPayload(
        val transactions: List<ExpenseTransaction>,
        val budgets: List<MonthBudgetConfig>,
        val backupDate: Long = System.currentTimeMillis(),
        val appVersion: String = "1.0"
    )

    fun createEncryptedBackupFile(
        context: Context,
        passphrase: String,
        payload: BackupPayload
    ): Pair<File, Uri> {
        val jsonString = serializePayload(payload)
        val encryptedBytes = encrypt(jsonString.toByteArray(StandardCharsets.UTF_8), passphrase)

        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val fileName = "CashTrace_Backup_${System.currentTimeMillis()}.rwenc"
        val file = File(backupDir, fileName)

        FileOutputStream(file).use { it.write(encryptedBytes) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    fun shareToGoogleDrive(context: Context, uri: Uri, fileName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Cash Trace Encrypted Cloud Backup")
            putExtra(
                Intent.EXTRA_TEXT,
                "Encrypted backup for Cash Trace Expenses. Save to Google Drive (free cloud storage) for safekeeping."
            )
            setPackage("com.google.android.apps.docs") // Google Drive target if installed
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard chooser if Google Drive specific package is not installed
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Cash Trace Encrypted Cloud Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Save to Google Drive / Cloud").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun restoreEncryptedBackup(
        context: Context,
        uri: Uri,
        passphrase: String
    ): BackupPayload {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not read backup file")

        val decryptedBytes = decrypt(bytes, passphrase)
        val jsonString = String(decryptedBytes, StandardCharsets.UTF_8)
        return deserializePayload(jsonString)
    }

    private fun encrypt(data: ByteArray, passphrase: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherText = cipher.doFinal(data)

        val output = ByteArrayOutputStream()
        output.write(salt)
        output.write(iv)
        output.write(cipherText)
        return output.toByteArray()
    }

    private fun decrypt(encryptedData: ByteArray, passphrase: String): ByteArray {
        if (encryptedData.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid backup file: header too short")
        }

        val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
        val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val cipherText = encryptedData.copyOfRange(SALT_LENGTH + IV_LENGTH, encryptedData.size)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(cipherText)
    }

    private fun serializePayload(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("version", payload.appVersion)
        root.put("backupDate", payload.backupDate)

        val txArray = JSONArray()
        for (tx in payload.transactions) {
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("title", tx.title)
            obj.put("amount", tx.amount)
            obj.put("type", tx.type.name)
            obj.put("category", tx.category)
            obj.put("paymentMode", tx.paymentMode.name)
            obj.put("timestamp", tx.timestamp)
            obj.put("note", tx.note)
            obj.put("sourceTag", tx.sourceTag)
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val bgArray = JSONArray()
        for (bg in payload.budgets) {
            val obj = JSONObject()
            obj.put("monthKey", bg.monthKey)
            obj.put("startingBalance", bg.startingBalance)
            obj.put("monthlyBudgetLimit", bg.monthlyBudgetLimit)
            obj.put("rolledOverFromPrevious", bg.rolledOverFromPrevious)
            obj.put("isAutoRollOverEnabled", bg.isAutoRollOverEnabled)
            bgArray.put(obj)
        }
        root.put("budgets", bgArray)

        return root.toString()
    }

    private fun deserializePayload(jsonString: String): BackupPayload {
        val root = JSONObject(jsonString)
        val txList = mutableListOf<ExpenseTransaction>()
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val obj = txArray.getJSONObject(i)
            txList.add(
                ExpenseTransaction(
                    id = obj.optLong("id", 0),
                    title = obj.getString("title"),
                    amount = obj.getDouble("amount"),
                    type = com.example.data.TransactionType.valueOf(obj.getString("type")),
                    category = obj.getString("category"),
                    paymentMode = try {
                        com.example.data.PaymentMode.valueOf(obj.getString("paymentMode"))
                    } catch (e: Exception) {
                        com.example.data.PaymentMode.UPI
                    },
                    timestamp = obj.getLong("timestamp"),
                    note = obj.optString("note", ""),
                    sourceTag = obj.optString("sourceTag", "MANUAL")
                )
            )
        }

        val bgList = mutableListOf<MonthBudgetConfig>()
        val bgArray = root.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until bgArray.length()) {
            val obj = bgArray.getJSONObject(i)
            bgList.add(
                MonthBudgetConfig(
                    monthKey = obj.getString("monthKey"),
                    startingBalance = obj.optDouble("startingBalance", 0.0),
                    monthlyBudgetLimit = obj.optDouble("monthlyBudgetLimit", 0.0),
                    rolledOverFromPrevious = obj.optDouble("rolledOverFromPrevious", 0.0),
                    isAutoRollOverEnabled = obj.optBoolean("isAutoRollOverEnabled", true)
                )
            )
        }

        return BackupPayload(
            transactions = txList,
            budgets = bgList,
            backupDate = root.optLong("backupDate", System.currentTimeMillis()),
            appVersion = root.optString("version", "1.0")
        )
    }
}
