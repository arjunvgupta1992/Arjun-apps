package com.example.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LastCapturedInfo
import com.example.data.local.PreferencesManager
import com.example.data.repository.TransactionRepository
import com.example.data.repository.UsageStatsRepository
import com.example.service.MoneyLensNotificationListener
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DataSourceUiState(
    val isNotificationListenerEnabled: Boolean = false,
    val isUsageAccessEnabled: Boolean = false,
    val isMonitoringPaused: Boolean = false,
    val isCloudBackupConsented: Boolean = false,
    val isBatteryOptDismissed: Boolean = false,
    val lastCaptured: LastCapturedInfo = LastCapturedInfo("None", 0L, "", 0.0),
    val totalTransactionsCount: Int = 0
)

class DataSourceViewModel(
    private val preferencesManager: PreferencesManager,
    private val transactionRepository: TransactionRepository,
    private val usageStatsRepository: UsageStatsRepository
) : ViewModel() {

    private val _isNotificationListenerEnabled = MutableStateFlow(false)
    private val _isUsageAccessEnabled = MutableStateFlow(false)

    val uiState: StateFlow<DataSourceUiState> = combine(
        _isNotificationListenerEnabled,
        _isUsageAccessEnabled,
        preferencesManager.isMonitoringEnabled,
        preferencesManager.isCloudBackupConsented,
        preferencesManager.isBatteryOptDismissed,
        preferencesManager.lastCapturedInfo,
        transactionRepository.allApprovedTransactions
    ) { args: Array<Any?> ->
        val notifEnabled = args[0] as Boolean
        val usageEnabled = args[1] as Boolean
        val isMonitoring = args[2] as Boolean
        val isCloud = args[3] as Boolean
        val isBatteryDismissed = args[4] as Boolean
        val lastCap = args[5] as LastCapturedInfo
        @Suppress("UNCHECKED_CAST")
        val allTx = args[6] as List<*>

        DataSourceUiState(
            isNotificationListenerEnabled = notifEnabled,
            isUsageAccessEnabled = usageEnabled,
            isMonitoringPaused = !isMonitoring,
            isCloudBackupConsented = isCloud,
            isBatteryOptDismissed = isBatteryDismissed,
            lastCaptured = lastCap,
            totalTransactionsCount = allTx.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DataSourceUiState()
    )

    fun refreshPermissionStates(context: Context) {
        _isNotificationListenerEnabled.value = isNotificationServiceEnabled(context)
        _isUsageAccessEnabled.value = usageStatsRepository.hasUsageStatsPermission()
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setMonitoringEnabled(enabled)
        }
    }

    fun toggleCloudBackupConsent(consented: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCloudBackupConsent(consented)
        }
    }

    fun dismissBatteryOpt() {
        viewModelScope.launch {
            preferencesManager.setBatteryOptDismissed(true)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            transactionRepository.deleteAllData()
            preferencesManager.clearAllPreferences()
        }
    }

    companion object {
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && cn.packageName == pkgName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}

class DataSourceViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val transactionRepository: TransactionRepository,
    private val usageStatsRepository: UsageStatsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DataSourceViewModel(preferencesManager, transactionRepository, usageStatsRepository) as T
    }
}
