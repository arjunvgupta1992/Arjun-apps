package com.example.util

import com.example.data.local.CategorySpendAggregate
import com.example.data.model.BudgetEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import java.util.Calendar

data class SpendingInsight(
    val title: String,
    val description: String,
    val type: InsightType // INFO, WARNING, SUCCESS, ALERT
)

enum class InsightType {
    INFO,
    WARNING,
    SUCCESS,
    ALERT
}

object AiInsightEngine {

    /**
     * Generates strictly factual, deterministic AI spending insights based ONLY on approved transactions.
     * Never fabricates missing transactions.
     */
    fun generateInsights(
        approvedTransactions: List<TransactionEntity>,
        categoryBreakdown: List<CategorySpendAggregate>,
        totalIncome: Double,
        totalExpense: Double,
        budget: BudgetEntity?
    ): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        if (approvedTransactions.isEmpty()) {
            insights.add(
                SpendingInsight(
                    title = "Awaiting Transactions",
                    description = "No approved transactions yet for this period. Add expenses manually or enable Notification Access to start capturing automatically.",
                    type = InsightType.INFO
                )
            )
            return insights
        }

        // 1. Budget Health Check
        if (budget != null && budget.monthlyLimit > 0.0) {
            val limit = budget.monthlyLimit
            val percentSpent = (totalExpense / limit) * 100.0
            val formattedSpent = CurrencyFormatter.formatInr(totalExpense)
            val formattedLimit = CurrencyFormatter.formatInr(limit)

            when {
                percentSpent >= 100.0 -> {
                    insights.add(
                        SpendingInsight(
                            title = "Budget Limit Exceeded",
                            description = "You have spent $formattedSpent exceeding your $formattedLimit monthly budget by ${(percentSpent - 100).toInt()}%. Consider trimming non-essential purchases.",
                            type = InsightType.ALERT
                        )
                    )
                }
                percentSpent >= 80.0 -> {
                    insights.add(
                        SpendingInsight(
                            title = "High Budget Utilization",
                            description = "You've used ${percentSpent.toInt()}% of your monthly budget ($formattedSpent of $formattedLimit). Only ${CurrencyFormatter.formatInr(limit - totalExpense)} remaining.",
                            type = InsightType.WARNING
                        )
                    )
                }
                else -> {
                    insights.add(
                        SpendingInsight(
                            title = "Budget On Track",
                            description = "You've used ${percentSpent.toInt()}% of your $formattedLimit limit ($formattedSpent spent). You are maintaining a healthy spending pace.",
                            type = InsightType.SUCCESS
                        )
                    )
                }
            }
        }

        // 2. Top Spending Category
        if (categoryBreakdown.isNotEmpty() && totalExpense > 0.0) {
            val topCategory = categoryBreakdown.first()
            val percentOfTotal = (topCategory.totalAmount / totalExpense) * 100.0
            val formattedCategoryAmount = CurrencyFormatter.formatInr(topCategory.totalAmount)

            insights.add(
                SpendingInsight(
                    title = "Primary Spending Driver: ${topCategory.category}",
                    description = "${topCategory.category} accounts for ${percentOfTotal.toInt()}% of your spending ($formattedCategoryAmount across ${topCategory.count} entries).",
                    type = InsightType.INFO
                )
            )
        }

        // 3. Peak Day Analysis
        val daySpends = mutableMapOf<Int, Double>()
        for (t in approvedTransactions.filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }) {
            val cal = Calendar.getInstance().apply { timeInMillis = t.timestamp }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            daySpends[dayOfWeek] = (daySpends[dayOfWeek] ?: 0.0) + t.amount
        }
        val peakDayEntry = daySpends.maxByOrNull { it.value }
        if (peakDayEntry != null && peakDayEntry.value > 0.0) {
            val dayName = when (peakDayEntry.key) {
                Calendar.SUNDAY -> "Sunday"
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                else -> "Day"
            }
            insights.add(
                SpendingInsight(
                    title = "Highest Spending on ${dayName}s",
                    description = "Your highest spending volume occurs on ${dayName}s (total ${CurrencyFormatter.formatInr(peakDayEntry.value)}). Planning weekend or mid-week runs can reduce spikes.",
                    type = InsightType.INFO
                )
            )
        }

        // 4. Net Savings Ratio
        if (totalIncome > 0.0) {
            val savings = totalIncome - totalExpense
            val savingsRate = (savings / totalIncome) * 100.0
            if (savings >= 0) {
                insights.add(
                    SpendingInsight(
                        title = "Savings Rate: ${savingsRate.toInt()}%",
                        description = "You've saved ${CurrencyFormatter.formatInr(savings)} from total income of ${CurrencyFormatter.formatInr(totalIncome)}.",
                        type = InsightType.SUCCESS
                    )
                )
            } else {
                insights.add(
                    SpendingInsight(
                        title = "Negative Cashflow Alert",
                        description = "Expenses (${CurrencyFormatter.formatInr(totalExpense)}) have outpaced income (${CurrencyFormatter.formatInr(totalIncome)}) by ${CurrencyFormatter.formatInr(-savings)} this period.",
                        type = InsightType.WARNING
                    )
                )
            }
        }

        return insights
    }
}
