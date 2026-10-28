package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MoneyLensDatabase
import com.example.data.local.TransactionDao
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.data.parser.DuplicateDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyLensDatabaseTest {

    private lateinit var db: MoneyLensDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var duplicateDetector: DuplicateDetector

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyLensDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
        duplicateDetector = DuplicateDetector(transactionDao)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndExcludeSelfTransferFromExpense() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Regular approved expense
        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 450.0,
                category = ExpenseCategory.FOOD.displayName,
                paymentMethod = PaymentMethod.UPI.displayName,
                merchant = "Swiggy",
                timestamp = now,
                status = TransactionStatus.APPROVED.name,
                isTransferSelf = false
            )
        )

        // 2. Self transfer of ₹10,000 (must be excluded from expense total!)
        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.TRANSFER.name,
                amount = 10000.0,
                category = ExpenseCategory.TRANSFERS.displayName,
                paymentMethod = PaymentMethod.BANK_TRANSFER.displayName,
                merchant = "Self Transfer",
                timestamp = now,
                status = TransactionStatus.APPROVED.name,
                isTransferSelf = true
            )
        )

        val totalExpense = transactionDao.getTotalExpenseBetween(now - 10000L, now + 10000L).first()
        assertEquals(450.0, totalExpense, 0.01)
    }

    @Test
    fun duplicateDetector_flagsDuplicateReferenceId() = runBlocking {
        val now = System.currentTimeMillis()
        val refId = "UPI429384928349"

        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 300.0,
                category = ExpenseCategory.FOOD.displayName,
                paymentMethod = PaymentMethod.UPI.displayName,
                merchant = "Chai Point",
                timestamp = now,
                referenceId = refId,
                status = TransactionStatus.APPROVED.name
            )
        )

        val checkResult = duplicateDetector.checkDuplicate(
            referenceId = refId,
            amount = 300.0,
            type = TransactionType.EXPENSE.name,
            timestamp = now + 5000L,
            merchant = "Chai Point"
        )

        assertTrue(checkResult.isDuplicate)
        assertTrue(checkResult.reason.contains(refId))
    }

    @Test
    fun searchTransactions_findsMatchingTag() = runBlocking {
        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 2500.0,
                category = ExpenseCategory.TRAVEL.displayName,
                paymentMethod = PaymentMethod.UPI.displayName,
                merchant = "Goa Resort",
                tags = "#vacation, #trip",
                timestamp = now,
                status = TransactionStatus.APPROVED.name
            )
        )

        val results = transactionDao.searchTransactions("vacation").first()
        assertEquals(1, results.size)
        assertEquals("Goa Resort", results[0].merchant)
    }
}
