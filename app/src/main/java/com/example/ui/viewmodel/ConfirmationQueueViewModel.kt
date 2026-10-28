package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransactionEntity
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfirmationQueueViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val pendingTransactions: StateFlow<List<TransactionEntity>> =
        transactionRepository.pendingTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun approveTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.approvePending(id)
        }
    }

    fun dismissTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.dismissPending(id)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteById(id)
        }
    }

    fun approveAllClean() {
        viewModelScope.launch {
            val pending = pendingTransactions.value
            for (t in pending) {
                if (!t.isPotentialDuplicate) {
                    transactionRepository.approvePending(t.id)
                }
            }
        }
    }
}

class ConfirmationQueueViewModelFactory(
    private val transactionRepository: TransactionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConfirmationQueueViewModel(transactionRepository) as T
    }
}
