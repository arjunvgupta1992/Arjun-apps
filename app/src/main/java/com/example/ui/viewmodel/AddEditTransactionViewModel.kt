package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddEditTransactionUiState(
    val transactionId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val category: String = ExpenseCategory.FOOD.displayName,
    val paymentMethod: String = PaymentMethod.UPI.displayName,
    val merchant: String = "",
    val account: String = "Primary Account",
    val toAccount: String = "",
    val isTransferSelf: Boolean = false,
    val referenceId: String = "",
    val note: String = "",
    val tagsText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

class AddEditTransactionViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditTransactionUiState())
    val uiState: StateFlow<AddEditTransactionUiState> = _uiState.asStateFlow()

    fun loadTransaction(id: Long) {
        viewModelScope.launch {
            val entity = transactionRepository.getTransactionById(id) ?: return@launch
            _uiState.value = AddEditTransactionUiState(
                transactionId = entity.id,
                type = try { TransactionType.valueOf(entity.type) } catch (_: Exception) { TransactionType.EXPENSE },
                amountText = entity.amount.toString(),
                category = entity.category,
                paymentMethod = entity.paymentMethod,
                merchant = entity.merchant,
                account = entity.account,
                toAccount = entity.toAccount ?: "",
                isTransferSelf = entity.isTransferSelf,
                referenceId = entity.referenceId,
                note = entity.note,
                tagsText = entity.tags,
                timestamp = entity.timestamp
            )
        }
    }

    fun updateType(type: TransactionType) {
        val defaultCategory = when (type) {
            TransactionType.EXPENSE -> ExpenseCategory.FOOD.displayName
            TransactionType.INCOME -> ExpenseCategory.SALARY.displayName
            TransactionType.TRANSFER -> ExpenseCategory.TRANSFERS.displayName
        }
        val isSelf = type == TransactionType.TRANSFER
        _uiState.value = _uiState.value.copy(
            type = type,
            category = defaultCategory,
            isTransferSelf = isSelf
        )
    }

    fun updateAmount(amount: String) {
        // Only allow numbers and one decimal point
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.value = _uiState.value.copy(amountText = amount, errorMessage = null)
        }
    }

    fun updateCategory(category: String) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun updatePaymentMethod(method: String) {
        _uiState.value = _uiState.value.copy(paymentMethod = method)
    }

    fun updateMerchant(merchant: String) {
        _uiState.value = _uiState.value.copy(merchant = merchant)
        // Auto deduce category if expense and category is currently default
        if (_uiState.value.type == TransactionType.EXPENSE && _uiState.value.category == ExpenseCategory.OTHER.displayName) {
            val deduced = com.example.data.parser.TransactionParser.deduceCategory(merchant, "", TransactionType.EXPENSE)
            _uiState.value = _uiState.value.copy(category = deduced.displayName)
        }
    }

    fun updateAccount(account: String) {
        _uiState.value = _uiState.value.copy(account = account)
    }

    fun updateToAccount(toAccount: String) {
        _uiState.value = _uiState.value.copy(toAccount = toAccount)
    }

    fun updateIsTransferSelf(isSelf: Boolean) {
        _uiState.value = _uiState.value.copy(isTransferSelf = isSelf)
    }

    fun updateReferenceId(refId: String) {
        _uiState.value = _uiState.value.copy(referenceId = refId)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun updateTags(tags: String) {
        _uiState.value = _uiState.value.copy(tagsText = tags)
    }

    fun toggleTag(tag: String) {
        val formatted = if (tag.startsWith("#")) tag else "#$tag"
        val currentTags = _uiState.value.tagsText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (currentTags.any { it.equals(formatted, ignoreCase = true) }) {
            currentTags.removeAll { it.equals(formatted, ignoreCase = true) }
        } else {
            currentTags.add(formatted)
        }
        _uiState.value = _uiState.value.copy(tagsText = currentTags.joinToString(", "))
    }

    fun updateTimestamp(timeMillis: Long) {
        _uiState.value = _uiState.value.copy(timestamp = timeMillis)
    }

    fun saveTransaction() {
        val state = _uiState.value
        val amount = state.amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.value = state.copy(errorMessage = "Please enter a valid amount greater than ₹0")
            return
        }

        val merchant = if (state.merchant.isBlank()) {
            if (state.type == TransactionType.TRANSFER) "Account Transfer" else "General"
        } else {
            state.merchant.trim()
        }

        viewModelScope.launch {
            val isTransfer = state.type == TransactionType.TRANSFER
            val entity = TransactionEntity(
                id = state.transactionId ?: 0L,
                type = state.type.name,
                amount = amount,
                category = state.category,
                paymentMethod = state.paymentMethod,
                merchant = merchant,
                timestamp = state.timestamp,
                note = state.note.trim(),
                tags = state.tagsText.trim(),
                account = state.account.trim().ifBlank { "Primary Account" },
                toAccount = if (isTransfer) state.toAccount.trim().ifBlank { "Savings Account" } else null,
                referenceId = state.referenceId.trim(),
                status = TransactionStatus.APPROVED.name,
                isTransferSelf = if (isTransfer) state.isTransferSelf else false,
                rawSource = "MANUAL",
                confidenceScore = 1.0f
            )

            if (state.transactionId != null && state.transactionId > 0) {
                transactionRepository.updateTransaction(entity)
            } else {
                transactionRepository.insertTransaction(entity)
            }

            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}

class AddEditTransactionViewModelFactory(
    private val transactionRepository: TransactionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddEditTransactionViewModel(transactionRepository) as T
    }
}
