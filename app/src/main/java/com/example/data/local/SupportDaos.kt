package com.example.data.local

import androidx.room.*
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryRuleEntity
import com.example.data.model.RecurringReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear")
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear AND category = :category LIMIT 1")
    suspend fun getBudget(monthYear: String, category: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBudget(budget: BudgetEntity): Long

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY keyword ASC")
    fun getAllRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun getAllRulesDirect(): List<CategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategoryRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CategoryRuleEntity>)

    @Delete
    suspend fun deleteRule(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}

@Dao
interface RecurringReminderDao {
    @Query("SELECT * FROM recurring_reminders ORDER BY dueDayOfMonth ASC")
    fun getAllReminders(): Flow<List<RecurringReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: RecurringReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: RecurringReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: RecurringReminderEntity)

    @Query("UPDATE recurring_reminders SET lastPaidMonthYear = :monthYear WHERE id = :id")
    suspend fun markAsPaidForMonth(id: Long, monthYear: String)
}
