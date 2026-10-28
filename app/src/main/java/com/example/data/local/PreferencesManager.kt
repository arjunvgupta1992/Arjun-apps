package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moneylens_settings")

class PreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val CLOUD_BACKUP_CONSENT = booleanPreferencesKey("cloud_backup_consent")
        val LAST_CAPTURED_SOURCE = stringPreferencesKey("last_captured_source")
        val LAST_CAPTURED_TIME = longPreferencesKey("last_captured_time")
        val LAST_CAPTURED_MERCHANT = stringPreferencesKey("last_captured_merchant")
        val LAST_CAPTURED_AMOUNT = doublePreferencesKey("last_captured_amount")
        val BATTERY_OPTIMIZATION_DISMISSED = booleanPreferencesKey("battery_opt_dismissed")
        val AUTO_APPROVE_HIGH_CONFIDENCE = booleanPreferencesKey("auto_approve_high_confidence")
        val PRIVACY_CONSENT_ACCEPTED = booleanPreferencesKey("privacy_consent_accepted")
    }

    val isMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.MONITORING_ENABLED] ?: true
        }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }

    val isCloudBackupConsented: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.CLOUD_BACKUP_CONSENT] ?: false
        }

    val lastCapturedInfo: Flow<LastCapturedInfo> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            LastCapturedInfo(
                source = preferences[PreferencesKeys.LAST_CAPTURED_SOURCE] ?: "None",
                timestamp = preferences[PreferencesKeys.LAST_CAPTURED_TIME] ?: 0L,
                merchant = preferences[PreferencesKeys.LAST_CAPTURED_MERCHANT] ?: "",
                amount = preferences[PreferencesKeys.LAST_CAPTURED_AMOUNT] ?: 0.0
            )
        }

    val isBatteryOptDismissed: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.BATTERY_OPTIMIZATION_DISMISSED] ?: false
        }

    val isAutoApproveEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_APPROVE_HIGH_CONFIDENCE] ?: false
        }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONITORING_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
            preferences[PreferencesKeys.PRIVACY_CONSENT_ACCEPTED] = true
        }
    }

    suspend fun setCloudBackupConsent(consented: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOUD_BACKUP_CONSENT] = consented
        }
    }

    suspend fun recordCapturedTransaction(source: String, merchant: String, amount: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CAPTURED_SOURCE] = source
            preferences[PreferencesKeys.LAST_CAPTURED_TIME] = System.currentTimeMillis()
            preferences[PreferencesKeys.LAST_CAPTURED_MERCHANT] = merchant
            preferences[PreferencesKeys.LAST_CAPTURED_AMOUNT] = amount
        }
    }

    suspend fun setBatteryOptDismissed(dismissed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BATTERY_OPTIMIZATION_DISMISSED] = dismissed
        }
    }

    suspend fun setAutoApproveEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_APPROVE_HIGH_CONFIDENCE] = enabled
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class LastCapturedInfo(
    val source: String,
    val timestamp: Long,
    val merchant: String,
    val amount: Double
)
