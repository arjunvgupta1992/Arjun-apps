package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category", "monthYear"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // Category name or "OVERALL"
    val monthlyLimit: Double,
    val monthYear: String // e.g. "2026-10"
)

@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String, // e.g., "swiggy", "uber", "netflix", "dmart"
    val targetCategory: String,
    val targetPaymentMethod: String = "UPI"
)

@Entity(tableName = "recurring_reminders")
data class RecurringReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String, // e.g. "Electricity Bill", "Rent", "Netflix"
    val expectedAmount: Double,
    val category: String,
    val dueDayOfMonth: Int, // 1 to 31
    val isAutoDetectPaid: Boolean = true,
    val lastPaidMonthYear: String? = null, // "2026-10"
    val notes: String = ""
)
