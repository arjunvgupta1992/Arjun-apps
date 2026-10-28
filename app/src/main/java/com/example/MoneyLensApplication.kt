package com.example

import android.app.Application
import com.example.data.local.MoneyLensDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.BudgetRepository
import com.example.data.repository.TransactionRepository
import com.example.data.repository.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MoneyLensApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy {
        MoneyLensDatabase.getDatabase(this, applicationScope)
    }

    val preferencesManager by lazy {
        PreferencesManager(this)
    }

    val transactionRepository by lazy {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            categoryRuleDao = database.categoryRuleDao(),
            preferencesManager = preferencesManager
        )
    }

    val budgetRepository by lazy {
        BudgetRepository(
            budgetDao = database.budgetDao(),
            categoryRuleDao = database.categoryRuleDao(),
            reminderDao = database.recurringReminderDao()
        )
    }

    val usageStatsRepository by lazy {
        UsageStatsRepository(this)
    }
}
