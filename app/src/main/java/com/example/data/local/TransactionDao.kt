package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class CategorySpendAggregate(
    val category: String,
    val totalAmount: Double,
    val count: Int
)

data class DailySpendAggregate(
    val dayTimestamp: Long,
    val totalAmount: Double
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE status = 'APPROVED' ORDER BY timestamp DESC")
    fun getAllApprovedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING_CONFIRMATION' ORDER BY timestamp DESC")
    fun getPendingConfirmationTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING_CONFIRMATION'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE status = 'APPROVED' 
        AND timestamp BETWEEN :startMillis AND :endMillis 
        ORDER BY timestamp DESC
    """)
    fun getTransactionsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE status = 'APPROVED' 
        AND (merchant LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR referenceId LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    // Self-transfers (isTransferSelf = 1) and transfers in general are excluded from income and expense totals
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE status = 'APPROVED' 
        AND type = 'INCOME' 
        AND isTransferSelf = 0
        AND timestamp BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalIncomeBetween(startMillis: Long, endMillis: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE status = 'APPROVED' 
        AND type = 'EXPENSE' 
        AND isTransferSelf = 0
        AND timestamp BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalExpenseBetween(startMillis: Long, endMillis: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE status = 'APPROVED' 
        AND type = 'EXPENSE' 
        AND isTransferSelf = 0
        AND timestamp >= :startOfDayMillis
    """)
    fun getTodaySpending(startOfDayMillis: Long): Flow<Double>

    // Category breakdown for expenses in date range
    @Query("""
        SELECT category, SUM(amount) as totalAmount, COUNT(id) as count 
        FROM transactions 
        WHERE status = 'APPROVED' 
        AND type = 'EXPENSE' 
        AND isTransferSelf = 0
        AND timestamp BETWEEN :startMillis AND :endMillis
        GROUP BY category 
        ORDER BY totalAmount DESC
    """)
    fun getCategoryExpenseBreakdown(startMillis: Long, endMillis: Long): Flow<List<CategorySpendAggregate>>

    // Deduplication check by Reference ID (e.g. UPI Ref / UTR)
    @Query("""
        SELECT * FROM transactions 
        WHERE referenceId = :refId 
        AND referenceId != '' 
        LIMIT 5
    """)
    suspend fun findByReferenceId(refId: String): List<TransactionEntity>

    // Deduplication check by matching amount and type within time window (+/- 15 minutes)
    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :type 
        AND timestamp BETWEEN :startTime AND :endTime 
        LIMIT 5
    """)
    suspend fun findPotentialFuzzyDuplicates(
        amount: Double,
        type: String,
        startTime: Long,
        endTime: Long
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestCapturedTransaction(): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 1")
    fun getLatestCapturedTransactionFlow(): Flow<TransactionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE transactions SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
