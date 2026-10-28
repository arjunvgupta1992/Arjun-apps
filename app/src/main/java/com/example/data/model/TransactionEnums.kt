package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class TransactionStatus {
    APPROVED,
    PENDING_CONFIRMATION,
    DISMISSED
}

enum class PaymentMethod(val displayName: String) {
    UPI("UPI"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    OTHER("Other")
}

enum class ExpenseCategory(
    val displayName: String,
    val iconName: String,
    val colorHex: Long
) {
    FOOD("Food & Dining", "Restaurant", 0xFFFF7043),
    GROCERIES("Groceries", "ShoppingCart", 0xFF26A69A),
    SHOPPING("Shopping", "ShoppingBag", 0xFFAB47BC),
    TRAVEL("Travel & Transport", "DirectionsCar", 0xFF42A5F5),
    FUEL("Fuel", "LocalGasStation", 0xFFFFA726),
    BILLS("Bills & Utilities", "ReceiptLong", 0xFFEF5350),
    HEALTHCARE("Healthcare", "MedicalServices", 0xFFEC407A),
    SUBSCRIPTIONS("Subscriptions", "Subscriptions", 0xFF7E57C2),
    EMI("EMI & Loans", "AccountBalance", 0xFF5C6BC0),
    RENT("Rent", "Home", 0xFF26C6DA),
    TRANSFERS("Transfers", "SwapHoriz", 0xFF29B6F6),
    BUSINESS("Business", "BusinessCenter", 0xFF8D6E63),
    SALARY("Salary & Income", "Payments", 0xFF00C853),
    INVESTMENT("Investment", "TrendingUp", 0xFF00B0FF),
    OTHER("Other", "Category", 0xFF78909C);

    companion object {
        fun fromString(name: String?): ExpenseCategory {
            if (name.isNullOrBlank()) return OTHER
            return entries.firstOrNull { 
                it.name.equals(name, ignoreCase = true) || 
                it.displayName.equals(name, ignoreCase = true) 
            } ?: OTHER
        }
    }
}
