package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(private val context: Context) {

    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    private val auth by lazy { Firebase.auth }

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before syncing with Firestore.")
    }

    suspend fun uploadLocalToFirestore(
        transactions: List<ExpenseTransaction>,
        budgets: List<MonthBudgetConfig>
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            var count = 0

            // Batch write transactions
            val txChunks = transactions.chunked(450)
            for (chunk in txChunks) {
                val batch = db.batch()
                for (tx in chunk) {
                    val docRef = db.collection("users")
                        .document(uid)
                        .collection("transactions")
                        .document(tx.id.toString())

                    val data = hashMapOf(
                        "id" to tx.id,
                        "title" to tx.title,
                        "amount" to tx.amount,
                        "type" to tx.type.name,
                        "category" to tx.category,
                        "paymentMode" to tx.paymentMode.name,
                        "timestamp" to tx.timestamp,
                        "note" to tx.note,
                        "sourceTag" to tx.sourceTag,
                        "userId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, data, SetOptions.merge())
                    count++
                }
                batch.commit().await()
            }

            // Batch write budgets
            if (budgets.isNotEmpty()) {
                val budgetBatch = db.batch()
                for (b in budgets) {
                    val docRef = db.collection("users")
                        .document(uid)
                        .collection("budgets")
                        .document(b.monthKey)

                    val data = hashMapOf(
                        "monthKey" to b.monthKey,
                        "startingBalance" to b.startingBalance,
                        "monthlyBudgetLimit" to b.monthlyBudgetLimit,
                        "rolledOverFromPrevious" to b.rolledOverFromPrevious,
                        "isAutoRollOverEnabled" to b.isAutoRollOverEnabled,
                        "userId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    budgetBatch.set(docRef, data, SetOptions.merge())
                }
                budgetBatch.commit().await()
            }

            Result.success(count)
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Error uploading to Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun downloadFromFirestore(): Result<Pair<List<ExpenseTransaction>, List<MonthBudgetConfig>>> {
        return try {
            val uid = requireUserId()

            // Fetch transactions
            val txSnapshot = db.collection("users")
                .document(uid)
                .collection("transactions")
                .get()
                .await()

            val txList = txSnapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                    val title = doc.getString("title") ?: "Transaction"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val typeStr = doc.getString("type") ?: "EXPENSE"
                    val type = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.EXPENSE }
                    val category = doc.getString("category") ?: "Other Expense"
                    val modeStr = doc.getString("paymentMode") ?: "UPI"
                    val mode = try { PaymentMode.valueOf(modeStr) } catch (e: Exception) { PaymentMode.UPI }
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val note = doc.getString("note") ?: ""
                    val sourceTag = doc.getString("sourceTag") ?: "CLOUD"

                    ExpenseTransaction(
                        id = id,
                        title = title,
                        amount = amount,
                        type = type,
                        category = category,
                        paymentMode = mode,
                        timestamp = timestamp,
                        note = note,
                        sourceTag = sourceTag
                    )
                } catch (e: Exception) {
                    null
                }
            }

            // Fetch budgets
            val budgetSnapshot = db.collection("users")
                .document(uid)
                .collection("budgets")
                .get()
                .await()

            val budgetList = budgetSnapshot.documents.mapNotNull { doc ->
                try {
                    val monthKey = doc.getString("monthKey") ?: doc.id
                    val startingBalance = doc.getDouble("startingBalance") ?: 0.0
                    val monthlyBudgetLimit = doc.getDouble("monthlyBudgetLimit") ?: 0.0
                    val rolledOver = doc.getDouble("rolledOverFromPrevious") ?: 0.0
                    val autoRollover = doc.getBoolean("isAutoRollOverEnabled") ?: true

                    MonthBudgetConfig(
                        monthKey = monthKey,
                        startingBalance = startingBalance,
                        monthlyBudgetLimit = monthlyBudgetLimit,
                        rolledOverFromPrevious = rolledOver,
                        isAutoRollOverEnabled = autoRollover
                    )
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(Pair(txList, budgetList))
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Error downloading from Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun syncSingleTransaction(tx: ExpenseTransaction): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            val docRef = db.collection("users")
                .document(uid)
                .collection("transactions")
                .document(tx.id.toString())

            val data = hashMapOf(
                "id" to tx.id,
                "title" to tx.title,
                "amount" to tx.amount,
                "type" to tx.type.name,
                "category" to tx.category,
                "paymentMode" to tx.paymentMode.name,
                "timestamp" to tx.timestamp,
                "note" to tx.note,
                "sourceTag" to tx.sourceTag,
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Error syncing single transaction", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSingleTransaction(txId: Long): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            db.collection("users")
                .document(uid)
                .collection("transactions")
                .document(txId.toString())
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Error deleting transaction from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun syncSingleBudget(budget: MonthBudgetConfig): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            val docRef = db.collection("users")
                .document(uid)
                .collection("budgets")
                .document(budget.monthKey)

            val data = hashMapOf(
                "monthKey" to budget.monthKey,
                "startingBalance" to budget.startingBalance,
                "monthlyBudgetLimit" to budget.monthlyBudgetLimit,
                "rolledOverFromPrevious" to budget.rolledOverFromPrevious,
                "isAutoRollOverEnabled" to budget.isAutoRollOverEnabled,
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Error syncing single budget", e)
            Result.failure(e)
        }
    }
}
