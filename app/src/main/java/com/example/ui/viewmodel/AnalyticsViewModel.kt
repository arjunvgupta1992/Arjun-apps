package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CategorySpendAggregate
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.TransactionRepository
import com.example.util.CsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.Calendar

enum class DateRangeFilter(val displayName: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_7_DAYS("Last 7 Days"),
    ALL_TIME("All Time")
}

data class AnalyticsUiState(
    val selectedRange: DateRangeFilter = DateRangeFilter.THIS_MONTH,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netSavings: Double = 0.0,
    val categoryBreakdown: List<CategorySpendAggregate> = emptyList(),
    val paymentMethodBreakdown: Map<String, Double> = emptyMap(),
    val dailySpendList: List<DaySpend> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val transactionCount: Int = 0
)

class AnalyticsViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(DateRangeFilter.THIS_MONTH)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        transactionRepository.allApprovedTransactions,
        _selectedRange
    ) { allTx, range ->
        val (startMillis, endMillis) = computeRangeBounds(range)

        val filtered = allTx.filter { it.timestamp in startMillis..endMillis }

        val totalIncome = filtered
            .filter { it.type == TransactionType.INCOME.name && !it.isTransferSelf }
            .sumOf { it.amount }

        val totalExpense = filtered
            .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
            .sumOf { it.amount }

        val netSavings = totalIncome - totalExpense

        val categoryBreakdown = filtered
            .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
            .groupBy { it.category }
            .map { (cat, list) ->
                CategorySpendAggregate(category = cat, totalAmount = list.sumOf { it.amount }, count = list.size)
            }
            .sortedByDescending { it.totalAmount }

        val paymentMethodBreakdown = filtered
            .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
            .groupBy { it.paymentMethod }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val dailyList = computeDailyTrend(filtered, startMillis, endMillis)

        AnalyticsUiState(
            selectedRange = range,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            categoryBreakdown = categoryBreakdown,
            paymentMethodBreakdown = paymentMethodBreakdown,
            dailySpendList = dailyList,
            filteredTransactions = filtered,
            transactionCount = filtered.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState()
    )

    fun selectRange(range: DateRangeFilter) {
        _selectedRange.value = range
    }

    fun exportCsv(context: Context): File? {
        val txs = uiState.value.filteredTransactions
        return CsvExporter.exportAndShareTransactions(context, txs)
    }

    private fun computeRangeBounds(range: DateRangeFilter): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        return when (range) {
            DateRangeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            DateRangeFilter.LAST_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val end = cal.timeInMillis - 1
                Pair(start, end)
            }
            DateRangeFilter.LAST_30_DAYS -> {
                Pair(now - 30L * 86400000L, now)
            }
            DateRangeFilter.LAST_7_DAYS -> {
                Pair(now - 7L * 86400000L, now)
            }
            DateRangeFilter.ALL_TIME -> {
                Pair(0L, now + 86400000L)
            }
        }
    }

    private fun computeDailyTrend(filtered: List<TransactionEntity>, startMillis: Long, endMillis: Long): List<DaySpend> {
        val result = mutableListOf<DaySpend>()
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)

        val daysCount = (((endMillis - startMillis) / 86400000L) + 1).toInt().coerceIn(1, 31)
        val dayFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())

        for (i in (daysCount - 1) downTo 0) {
            val dCal = Calendar.getInstance().apply {
                timeInMillis = endMillis
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dStart = dCal.timeInMillis
            val dEnd = dStart + 86400000L - 1

            val total = filtered
                .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf && it.timestamp in dStart..dEnd }
                .sumOf { it.amount }

            val isToday = dCal.get(Calendar.DAY_OF_YEAR) == today
            result.add(
                DaySpend(
                    dayLabel = dayFormat.format(dCal.time),
                    fullDate = dayFormat.format(dCal.time),
                    amount = total,
                    isToday = isToday
                )
            )
        }
        return result
    }
}

class AnalyticsViewModelFactory(
    private val transactionRepository: TransactionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnalyticsViewModel(transactionRepository) as T
    }
}
