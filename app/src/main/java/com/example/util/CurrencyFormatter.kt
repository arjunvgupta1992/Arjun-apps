package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private val decimalFormat = DecimalFormat("#,##,##0.00")
    private val wholeFormat = DecimalFormat("#,##,##0")

    fun formatInr(amount: Double, showDecimals: Boolean = false): String {
        val formatted = if (showDecimals || amount % 1.0 != 0.0) {
            decimalFormat.format(amount)
        } else {
            wholeFormat.format(amount)
        }
        return "₹$formatted"
    }

    fun formatCompactInr(amount: Double): String {
        return when {
            amount >= 1_00_00_000 -> "₹${String.format(Locale.ROOT, "%.2f", amount / 1_00_00_000)} Cr"
            amount >= 1_00_000 -> "₹${String.format(Locale.ROOT, "%.1f", amount / 1_00_000)} L"
            amount >= 1_000 -> "₹${String.format(Locale.ROOT, "%.1f", amount / 1_000)} k"
            else -> "₹${amount.toInt()}"
        }
    }

    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
