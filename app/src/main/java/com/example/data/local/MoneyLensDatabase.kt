package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryRuleEntity
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.RecurringReminderEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        CategoryRuleEntity::class,
        RecurringReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MoneyLensDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun recurringReminderDao(): RecurringReminderDao

    companion object {
        @Volatile
        private var INSTANCE: MoneyLensDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): MoneyLensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MoneyLensDatabase::class.java,
                    "moneylens_database"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        prepopulateCategoryRules(database.categoryRuleDao())
                        prepopulateInitialData(database.budgetDao(), database.recurringReminderDao())
                    }
                }
            }
        }

        private suspend fun prepopulateCategoryRules(ruleDao: CategoryRuleDao) {
            val defaultRules = listOf(
                CategoryRuleEntity(keyword = "swiggy", targetCategory = ExpenseCategory.FOOD.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "zomato", targetCategory = ExpenseCategory.FOOD.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "mcdonalds", targetCategory = ExpenseCategory.FOOD.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "starbucks", targetCategory = ExpenseCategory.FOOD.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "blinkit", targetCategory = ExpenseCategory.GROCERIES.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "zepto", targetCategory = ExpenseCategory.GROCERIES.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "instamart", targetCategory = ExpenseCategory.GROCERIES.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "dmart", targetCategory = ExpenseCategory.GROCERIES.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "bigbasket", targetCategory = ExpenseCategory.GROCERIES.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "uber", targetCategory = ExpenseCategory.TRAVEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "ola", targetCategory = ExpenseCategory.TRAVEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "rapido", targetCategory = ExpenseCategory.TRAVEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "irctc", targetCategory = ExpenseCategory.TRAVEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "makemytrip", targetCategory = ExpenseCategory.TRAVEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "amazon", targetCategory = ExpenseCategory.SHOPPING.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "flipkart", targetCategory = ExpenseCategory.SHOPPING.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "myntra", targetCategory = ExpenseCategory.SHOPPING.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "shell", targetCategory = ExpenseCategory.FUEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "hpcl", targetCategory = ExpenseCategory.FUEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "bpcl", targetCategory = ExpenseCategory.FUEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "ioc", targetCategory = ExpenseCategory.FUEL.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "bescom", targetCategory = ExpenseCategory.BILLS.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "tneb", targetCategory = ExpenseCategory.BILLS.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "airtel", targetCategory = ExpenseCategory.BILLS.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "jio", targetCategory = ExpenseCategory.BILLS.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "act fibernet", targetCategory = ExpenseCategory.BILLS.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "netflix", targetCategory = ExpenseCategory.SUBSCRIPTIONS.displayName, targetPaymentMethod = PaymentMethod.CREDIT_CARD.displayName),
                CategoryRuleEntity(keyword = "spotify", targetCategory = ExpenseCategory.SUBSCRIPTIONS.displayName, targetPaymentMethod = PaymentMethod.CREDIT_CARD.displayName),
                CategoryRuleEntity(keyword = "youtube", targetCategory = ExpenseCategory.SUBSCRIPTIONS.displayName, targetPaymentMethod = PaymentMethod.CREDIT_CARD.displayName),
                CategoryRuleEntity(keyword = "prime", targetCategory = ExpenseCategory.SUBSCRIPTIONS.displayName, targetPaymentMethod = PaymentMethod.CREDIT_CARD.displayName),
                CategoryRuleEntity(keyword = "apollo", targetCategory = ExpenseCategory.HEALTHCARE.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "pharmeasy", targetCategory = ExpenseCategory.HEALTHCARE.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName),
                CategoryRuleEntity(keyword = "1mg", targetCategory = ExpenseCategory.HEALTHCARE.displayName, targetPaymentMethod = PaymentMethod.UPI.displayName)
            )
            ruleDao.insertAll(defaultRules)
        }

        private suspend fun prepopulateInitialData(budgetDao: BudgetDao, reminderDao: RecurringReminderDao) {
            val currentMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
            // Overall monthly budget limit default
            budgetDao.insertOrUpdateBudget(
                BudgetEntity(category = "OVERALL", monthlyLimit = 35000.0, monthYear = currentMonth)
            )
            // Initial common recurring reminders
            reminderDao.insertReminder(
                RecurringReminderEntity(
                    title = "Wifi / Fiber Internet",
                    expectedAmount = 943.0,
                    category = ExpenseCategory.BILLS.displayName,
                    dueDayOfMonth = 10,
                    notes = "Monthly broadband renewal"
                )
            )
            reminderDao.insertReminder(
                RecurringReminderEntity(
                    title = "Mobile Postpaid / Recharge",
                    expectedAmount = 719.0,
                    category = ExpenseCategory.BILLS.displayName,
                    dueDayOfMonth = 15,
                    notes = "Primary SIM pack"
                )
            )
        }
    }
}
