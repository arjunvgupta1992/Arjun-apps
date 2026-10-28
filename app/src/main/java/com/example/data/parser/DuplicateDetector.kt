package com.example.data.parser

import com.example.data.local.TransactionDao
import com.example.data.model.TransactionEntity

data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val reason: String = "",
    val matchingTransactionId: Long? = null
)

class DuplicateDetector(private val transactionDao: TransactionDao) {

    /**
     * Check if a candidate transaction already exists in the database.
     * Uses Reference ID (primary) and Fuzzy Amount + Window (secondary).
     */
    suspend fun checkDuplicate(
        referenceId: String,
        amount: Double,
        type: String,
        timestamp: Long,
        merchant: String,
        timeWindowMillis: Long = 15 * 60 * 1000L // 15 minutes window
    ): DuplicateCheckResult {

        // 1. Exact Reference ID match
        if (referenceId.isNotBlank()) {
            val refMatches = transactionDao.findByReferenceId(referenceId)
            if (refMatches.isNotEmpty()) {
                val match = refMatches.first()
                return DuplicateCheckResult(
                    isDuplicate = true,
                    reason = "Matching Reference ID: $referenceId (already recorded)",
                    matchingTransactionId = match.id
                )
            }
        }

        // 2. Fuzzy match: same amount, same type, within +/- 15 minutes
        val startTime = timestamp - timeWindowMillis
        val endTime = timestamp + timeWindowMillis
        val fuzzyMatches = transactionDao.findPotentialFuzzyDuplicates(
            amount = amount,
            type = type,
            startTime = startTime,
            endTime = endTime
        )

        for (match in fuzzyMatches) {
            val merchantMatch = merchant.isNotBlank() && (
                match.merchant.contains(merchant, ignoreCase = true) || 
                merchant.contains(match.merchant, ignoreCase = true)
            )
            if (merchantMatch) {
                return DuplicateCheckResult(
                    isDuplicate = true,
                    reason = "Similar ₹$amount transaction at ${match.merchant} recorded within 15 minutes",
                    matchingTransactionId = match.id
                )
            }
        }

        // If amount and type match closely in time even with generic merchant
        if (fuzzyMatches.isNotEmpty()) {
            val match = fuzzyMatches.first()
            return DuplicateCheckResult(
                isDuplicate = true,
                reason = "Potential duplicate: ₹$amount $type within 15 mins of existing entry #${match.id}",
                matchingTransactionId = match.id
            )
        }

        return DuplicateCheckResult(isDuplicate = false)
    }
}
