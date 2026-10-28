package com.example.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.example.data.local.BudgetDao
import com.example.data.local.CategoryRuleDao
import com.example.data.local.RecurringReminderDao
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryRuleEntity
import com.example.data.model.RecurringReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val reminderDao: RecurringReminderDao
) {
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(monthYear)

    suspend fun setBudget(category: String, amount: Double, monthYear: String) {
        val existing = budgetDao.getBudget(monthYear, category)
        val entity = existing?.copy(monthlyLimit = amount) ?: BudgetEntity(
            category = category,
            monthlyLimit = amount,
            monthYear = monthYear
        )
        budgetDao.insertOrUpdateBudget(entity)
    }

    val categoryRules: Flow<List<CategoryRuleEntity>> = categoryRuleDao.getAllRules()

    suspend fun addCategoryRule(keyword: String, category: String, paymentMethod: String = "UPI") {
        categoryRuleDao.insertRule(
            CategoryRuleEntity(keyword = keyword.trim().lowercase(Locale.ROOT), targetCategory = category, targetPaymentMethod = paymentMethod)
        )
    }

    suspend fun deleteCategoryRule(id: Long) = categoryRuleDao.deleteRuleById(id)

    val recurringReminders: Flow<List<RecurringReminderEntity>> = reminderDao.getAllReminders()

    suspend fun addRecurringReminder(reminder: RecurringReminderEntity) =
        reminderDao.insertReminder(reminder)

    suspend fun deleteRecurringReminder(reminder: RecurringReminderEntity) =
        reminderDao.deleteReminder(reminder)

    suspend fun markReminderPaid(id: Long, monthYear: String) =
        reminderDao.markAsPaidForMonth(id, monthYear)
}

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val usageDurationMillis: Long,
    val category: String, // Shopping, Food, Finance, Social, Entertainment, Other
    val lastTimeUsed: Long
)

class UsageStatsRepository(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    /**
     * Check if Usage Access permission is granted by the user in Android Settings.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Query app usage for the specified day range (e.g. today or last N days).
     * Strictly processes locally and classifies spending trigger apps (e.g., Shopping, Food).
     */
    suspend fun getAppUsageList(daysAgo: Int = 0): List<AppUsageItem> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission() || usageStatsManager == null) {
            return@withContext emptyList()
        }

        val calendar = Calendar.getInstance().apply {
            if (daysAgo > 0) {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = if (daysAgo == 0) System.currentTimeMillis() else (calendar.timeInMillis + 86400000L)

        val stats: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        // Filter and aggregate apps with visible foreground usage (> 1 minute)
        stats.filter { it.totalTimeInForeground > 60_000L }
            .sortedByDescending { it.totalTimeInForeground }
            .map { stat ->
                val appName = resolveAppName(stat.packageName)
                val category = categorizeApp(stat.packageName, appName)
                AppUsageItem(
                    packageName = stat.packageName,
                    appName = appName,
                    usageDurationMillis = stat.totalTimeInForeground,
                    category = category,
                    lastTimeUsed = stat.lastTimeUsed
                )
            }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            // Friendly fallback from package identifier
            packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }

    private fun categorizeApp(packageName: String, appName: String): String {
        val lower = (packageName + " " + appName).lowercase(Locale.ROOT)
        return when {
            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") ||
            lower.contains("meesho") || lower.contains("ajio") || lower.contains("nykaa") ||
            lower.contains("tata cliq") || lower.contains("shops") -> "Shopping"

            lower.contains("swiggy") || lower.contains("zomato") || lower.contains("zepto") ||
            lower.contains("blinkit") || lower.contains("instamart") || lower.contains("dmart") ||
            lower.contains("eats") || lower.contains("mcdonald") -> "Food & Groceries"

            lower.contains("gpay") || lower.contains("paisa") || lower.contains("phonepe") ||
            lower.contains("paytm") || lower.contains("cred") || lower.contains("zerodha") ||
            lower.contains("groww") || lower.contains("hdfc") || lower.contains("sbi") ||
            lower.contains("icici") || lower.contains("axis") || lower.contains("bank") -> "Finance & Payments"

            lower.contains("instagram") || lower.contains("facebook") || lower.contains("whatsapp") ||
            lower.contains("telegram") || lower.contains("snapchat") || lower.contains("twitter") ||
            lower.contains("x.com") || lower.contains("reddit") -> "Social Media"

            lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify") ||
            lower.contains("primevideo") || lower.contains("hotstar") || lower.contains("games") -> "Entertainment"

            else -> "Other"
        }
    }
}
