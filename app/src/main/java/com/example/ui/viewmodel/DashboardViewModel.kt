package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CategorySpendAggregate
import com.example.data.local.PreferencesManager
import com.example.data.model.BudgetEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.BudgetRepository
import com.example.data.repository.IngestResult
import com.example.data.repository.TransactionRepository
import com.example.util.AiInsightEngine
import com.example.util.SpendingInsight
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DaySpend(
    val dayLabel: String, // "Mon", "Tue"
    val fullDate: String,
    val amount: Double,
    val isToday: Boolean
)

data class DashboardUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val estimatedBalance: Double = 0.0,
    val todaySpending: Double = 0.0,
    val isBalanceVisible: Boolean = true,
    val budget: BudgetEntity? = null,
    val budgetProgressPercent: Float = 0f,
    val sevenDaySpending: List<DaySpend> = emptyList(),
    val categoryBreakdown: List<CategorySpendAggregate> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val pendingQueueCount: Int = 0,
    val aiInsights: List<SpendingInsight> = emptyList(),
    val selectedFilter: String = "ALL", // ALL, EXPENSE, INCOME, TRANSFER
    val selectedTag: String = "ALL", // ALL, #work, #vacation, etc.
    val availableTags: List<String> = emptyList(),
    val searchQuery: String = "",
    val isMonitoringActive: Boolean = true,
    val latestCaptured: TransactionEntity? = null
)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isBalanceVisible = MutableStateFlow(true)
    private val _selectedFilter = MutableStateFlow("ALL")
    private val _selectedTag = MutableStateFlow("ALL")
    private val _searchQuery = MutableStateFlow("")

    private val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    // Start and end of current month
    private val monthRange: Pair<Long, Long>
        get() {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis - 1
            return Pair(start, end)
        }

    val uiState: StateFlow<DashboardUiState> = combine(
        transactionRepository.allApprovedTransactions,
        transactionRepository.pendingCount,
        budgetRepository.getBudgetsForMonth(currentMonthYear),
        preferencesManager.isMonitoringEnabled,
        _isBalanceVisible,
        _selectedFilter,
        _selectedTag,
        _searchQuery
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allTx = args[0] as List<TransactionEntity>
        val pendingCount = args[1] as Int
        @Suppress("UNCHECKED_CAST")
        val budgets = args[2] as List<BudgetEntity>
        val isMonitoring = args[3] as Boolean
        val isVisible = args[4] as Boolean
        val filter = args[5] as String
        val selectedTag = args[6] as String
        val query = args[7] as String

        val (monthStart, monthEnd) = monthRange
        val monthTransactions = allTx.filter { it.timestamp in monthStart..monthEnd }

        // Totals excluding self-transfers
        val totalIncome = monthTransactions
            .filter { it.type == TransactionType.INCOME.name && !it.isTransferSelf }
            .sumOf { it.amount }

        val totalExpense = monthTransactions
            .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
            .sumOf { it.amount }

        // All time estimated balance
        val allIncome = allTx.filter { it.type == TransactionType.INCOME.name && !it.isTransferSelf }.sumOf { it.amount }
        val allExpense = allTx.filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }.sumOf { it.amount }
        val balance = allIncome - allExpense

        // Today's spending
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todaySpending = allTx
            .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf && it.timestamp >= calToday.timeInMillis }
            .sumOf { it.amount }

        // Overall budget
        val overallBudget = budgets.firstOrNull { it.category == "OVERALL" }
        val budgetProgress = if (overallBudget != null && overallBudget.monthlyLimit > 0.0) {
            (totalExpense / overallBudget.monthlyLimit).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        // Category breakdown for current month
        val categoryBreakdown = monthTransactions
            .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
            .groupBy { it.category }
            .map { (cat, list) ->
                CategorySpendAggregate(
                    category = cat,
                    totalAmount = list.sumOf { it.amount },
                    count = list.size
                )
            }
            .sortedByDescending { it.totalAmount }

        // 7-day spending calculation
        val sevenDayList = computeSevenDaySpend(allTx)

        // Extract available tags from all transactions + common presets
        val tagsSet = linkedSetOf<String>()
        allTx.forEach { tx ->
            if (tx.tags.isNotBlank()) {
                tx.tags.split(",").forEach { raw ->
                    val t = raw.trim()
                    if (t.isNotEmpty()) {
                        tagsSet.add(if (t.startsWith("#")) t else "#$t")
                    }
                }
            }
        }
        val availableTags = tagsSet.toList()

        // Filter recent transactions
        var filteredList = allTx
        if (filter != "ALL") {
            filteredList = filteredList.filter { it.type == filter }
        }
        if (selectedTag != "ALL") {
            filteredList = filteredList.filter {
                it.tags.contains(selectedTag, ignoreCase = true) ||
                it.tags.contains(selectedTag.removePrefix("#"), ignoreCase = true)
            }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase(Locale.ROOT)
            filteredList = filteredList.filter {
                it.merchant.lowercase(Locale.ROOT).contains(q) ||
                it.category.lowercase(Locale.ROOT).contains(q) ||
                it.note.lowercase(Locale.ROOT).contains(q) ||
                it.tags.lowercase(Locale.ROOT).contains(q) ||
                it.referenceId.lowercase(Locale.ROOT).contains(q)
            }
        }

        // AI Insights strictly based on approved data
        val insights = AiInsightEngine.generateInsights(
            approvedTransactions = monthTransactions,
            categoryBreakdown = categoryBreakdown,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            budget = overallBudget
        )

        DashboardUiState(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            estimatedBalance = balance,
            todaySpending = todaySpending,
            isBalanceVisible = isVisible,
            budget = overallBudget,
            budgetProgressPercent = budgetProgress,
            sevenDaySpending = sevenDayList,
            categoryBreakdown = categoryBreakdown,
            recentTransactions = filteredList.take(25),
            pendingQueueCount = pendingCount,
            aiInsights = insights,
            selectedFilter = filter,
            selectedTag = selectedTag,
            availableTags = availableTags,
            searchQuery = query,
            isMonitoringActive = isMonitoring
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun toggleBalanceVisibility() {
        _isBalanceVisible.value = !_isBalanceVisible.value
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setSelectedTag(tag: String) {
        _selectedTag.value = tag
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
        }
    }

    fun ingestRawMessage(rawText: String, onResult: (IngestResult) -> Unit) {
        viewModelScope.launch {
            val res = transactionRepository.processAndIngest(
                rawText = rawText,
                source = "MANUAL_IMPORT"
            )
            onResult(res)
        }
    }

    private fun computeSevenDaySpend(allTx: List<TransactionEntity>): List<DaySpend> {
        val result = mutableListOf<DaySpend>()
        val cal = Calendar.getInstance()
        val todayDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(Calendar.YEAR)

        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val fullFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startDay = targetCal.timeInMillis
            val endDay = startDay + 86400000L - 1

            val dayTotal = allTx
                .filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf && it.timestamp in startDay..endDay }
                .sumOf { it.amount }

            val isToday = targetCal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear &&
                          targetCal.get(Calendar.YEAR) == todayYear

            result.add(
                DaySpend(
                    dayLabel = if (isToday) "Today" else dayFormat.format(targetCal.time),
                    fullDate = fullFormat.format(targetCal.time),
                    amount = dayTotal,
                    isToday = isToday
                )
            )
        }
        return result
    }
}

class DashboardViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val preferencesManager: PreferencesManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardViewModel(transactionRepository, budgetRepository, preferencesManager) as T
    }
}
