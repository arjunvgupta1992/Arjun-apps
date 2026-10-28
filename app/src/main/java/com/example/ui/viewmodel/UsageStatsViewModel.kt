package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppUsageItem
import com.example.data.repository.UsageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsageStatsUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val selectedDayTab: Int = 0, // 0 = Today, 1 = Yesterday
    val usageList: List<AppUsageItem> = emptyList(),
    val totalScreenTimeMillis: Long = 0L,
    val totalShoppingTimeMillis: Long = 0L,
    val totalFoodTimeMillis: Long = 0L
)

class UsageStatsViewModel(
    private val usageStatsRepository: UsageStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageStatsUiState())
    val uiState: StateFlow<UsageStatsUiState> = _uiState.asStateFlow()

    fun checkAndLoad() {
        val hasPerm = usageStatsRepository.hasUsageStatsPermission()
        _uiState.value = _uiState.value.copy(hasPermission = hasPerm)
        if (hasPerm) {
            loadUsageForTab(_uiState.value.selectedDayTab)
        }
    }

    fun selectDayTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedDayTab = tab)
        loadUsageForTab(tab)
    }

    private fun loadUsageForTab(tab: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = usageStatsRepository.getAppUsageList(daysAgo = tab)
            val totalTime = list.sumOf { it.usageDurationMillis }
            val shoppingTime = list.filter { it.category == "Shopping" }.sumOf { it.usageDurationMillis }
            val foodTime = list.filter { it.category == "Food & Groceries" }.sumOf { it.usageDurationMillis }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                usageList = list,
                totalScreenTimeMillis = totalTime,
                totalShoppingTimeMillis = shoppingTime,
                totalFoodTimeMillis = foodTime
            )
        }
    }
}

class UsageStatsViewModelFactory(
    private val usageStatsRepository: UsageStatsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UsageStatsViewModel(usageStatsRepository) as T
    }
}
