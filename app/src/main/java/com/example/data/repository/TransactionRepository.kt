package com.example.data.repository

import com.example.data.local.CategorySpendAggregate
import com.example.data.local.CategoryRuleDao
import com.example.data.local.PreferencesManager
import com.example.data.local.TransactionDao
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionStatus
import com.example.data.parser.DuplicateDetector
import com.example.data.parser.ParsedTransaction
import com.example.data.parser.TransactionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.Locale

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val preferencesManager: PreferencesManager
) {
    val duplicateDetector = DuplicateDetector(transactionDao)

    val allApprovedTransactions: Flow<List<TransactionEntity>> =
        transactionDao.getAllApprovedTransactions()

    val pendingTransactions: Flow<List<TransactionEntity>> =
        transactionDao.getPendingConfirmationTransactions()

    val pendingCount: Flow<Int> = transactionDao.getPendingCount()

    val latestCapturedTransactionFlow: Flow<TransactionEntity?> =
        transactionDao.getLatestCapturedTransactionFlow()

    fun getTransactionsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsBetween(startMillis, endMillis)

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        transactionDao.searchTransactions(query)

    fun getTotalIncome(startMillis: Long, endMillis: Long): Flow<Double> =
        transactionDao.getTotalIncomeBetween(startMillis, endMillis)

    fun getTotalExpense(startMillis: Long, endMillis: Long): Flow<Double> =
        transactionDao.getTotalExpenseBetween(startMillis, endMillis)

    fun getTodaySpending(): Flow<Double> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return transactionDao.getTodaySpending(cal.timeInMillis)
    }

    fun getCategoryExpenseBreakdown(startMillis: Long, endMillis: Long): Flow<List<CategorySpendAggregate>> =
        transactionDao.getCategoryExpenseBreakdown(startMillis, endMillis)

    suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.getTransactionById(id)

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insert(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.delete(transaction)

    suspend fun deleteById(id: Long) =
        transactionDao.deleteById(id)

    suspend fun approvePending(id: Long) =
        transactionDao.updateStatus(id, TransactionStatus.APPROVED.name)

    suspend fun dismissPending(id: Long) =
        transactionDao.updateStatus(id, TransactionStatus.DISMISSED.name)

    suspend fun deleteAllData() =
        transactionDao.deleteAll()

    /**
     * Ingest an incoming message text (from notification or manual paste / SMS import).
     * Extracts fields, applies user rules, runs duplicate detection, and persists.
     */
    suspend fun processAndIngest(
        rawText: String,
        packageName: String? = null,
        source: String = "NOTIFICATION",
        timestamp: Long = System.currentTimeMillis()
    ): IngestResult {
        val parsed: ParsedTransaction = TransactionParser.parse(rawText, packageName)
            ?: return IngestResult.Ignored("Not an eligible financial transaction or contains sensitive OTP/code.")

        // Check user custom category rules to override category if match found
        var finalCategory = parsed.category
        val customRules = categoryRuleDao.getAllRulesDirect()
        val lowerMerchant = parsed.merchant.lowercase(Locale.ROOT)
        for (rule in customRules) {
            if (lowerMerchant.contains(rule.keyword.lowercase(Locale.ROOT))) {
                finalCategory = rule.targetCategory
                break
            }
        }

        // Duplicate Detection
        val dupResult = duplicateDetector.checkDuplicate(
            referenceId = parsed.referenceId,
            amount = parsed.amount,
            type = parsed.type.name,
            timestamp = timestamp,
            merchant = parsed.merchant
        )

        val autoApprove = preferencesManager.isAutoApproveEnabled.firstOrNull() ?: false
        val status = when {
            dupResult.isDuplicate -> TransactionStatus.PENDING_CONFIRMATION.name
            autoApprove && parsed.confidenceScore >= 0.90f -> TransactionStatus.APPROVED.name
            else -> TransactionStatus.PENDING_CONFIRMATION.name
        }

        val entity = TransactionEntity(
            type = parsed.type.name,
            amount = parsed.amount,
            category = finalCategory,
            paymentMethod = parsed.paymentMethod.displayName,
            merchant = parsed.merchant,
            timestamp = timestamp,
            note = if (dupResult.isDuplicate) "[Flagged Duplicate: ${dupResult.reason}]" else "",
            account = parsed.account,
            referenceId = parsed.referenceId,
            status = status,
            isTransferSelf = parsed.isTransferSelf,
            rawSource = source,
            rawMessage = rawText,
            tags = parsed.tags,
            confidenceScore = parsed.confidenceScore,
            isPotentialDuplicate = dupResult.isDuplicate
        )

        val newId = transactionDao.insert(entity)
        preferencesManager.recordCapturedTransaction(
            source = source,
            merchant = parsed.merchant,
            amount = parsed.amount
        )

        return IngestResult.Success(
            id = newId,
            parsed = parsed,
            isDuplicate = dupResult.isDuplicate,
            duplicateReason = dupResult.reason,
            status = status
        )
    }
}

sealed class IngestResult {
    data class Success(
        val id: Long,
        val parsed: ParsedTransaction,
        val isDuplicate: Boolean,
        val duplicateReason: String,
        val status: String
    ) : IngestResult()

    data class Ignored(val reason: String) : IngestResult()
}
