package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["referenceId"]),
        Index(value = ["timestamp"]),
        Index(value = ["status"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // EXPENSE, INCOME, TRANSFER
    val amount: Double,
    val category: String, // Category name
    val paymentMethod: String, // UPI, CASH, etc.
    val merchant: String, // Merchant or payee/payer
    val timestamp: Long, // Epoch millis
    val note: String = "",
    val account: String = "Default Account", // Bank account or wallet
    val toAccount: String? = null, // For transfers
    val referenceId: String = "", // UPI Ref / UTR / Txn ID
    val status: String = TransactionStatus.APPROVED.name, // APPROVED, PENDING_CONFIRMATION, DISMISSED
    val isTransferSelf: Boolean = false, // If true, excluded from income and expense aggregates!
    val rawSource: String = "MANUAL", // MANUAL, NOTIFICATION, SMS_IMPORT
    val rawMessage: String? = null,
    val tags: String = "", // Comma-separated or hash-prefixed custom labels, e.g. "#work, #vacation"
    val confidenceScore: Float = 1.0f, // 0.0 to 1.0 confidence from parser
    val isPotentialDuplicate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
