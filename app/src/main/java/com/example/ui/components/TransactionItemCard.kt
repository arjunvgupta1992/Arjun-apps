package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExpenseCategory
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.SkyTransfer
import com.example.util.CurrencyFormatter

@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val categoryEnum = ExpenseCategory.fromString(transaction.category)
    val isExpense = transaction.type == TransactionType.EXPENSE.name
    val isIncome = transaction.type == TransactionType.INCOME.name
    val isTransfer = transaction.type == TransactionType.TRANSFER.name

    val amountColor = when {
        isExpense -> CoralExpense
        isIncome -> EmeraldLight
        else -> SkyTransfer
    }

    val amountPrefix = when {
        isExpense -> "- "
        isIncome -> "+ "
        else -> "⇄ "
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("transaction_item_${transaction.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Duplicate warning banner if flagged
            if (transaction.isPotentialDuplicate) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Duplicate Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Potential Duplicate: ${transaction.note.ifBlank { "Identical entry found" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Icon Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(categoryEnum.colorHex).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryVector(categoryEnum),
                        contentDescription = transaction.category,
                        tint = Color(categoryEnum.colorHex),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Merchant, and Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant.ifBlank { transaction.category },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = transaction.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = transaction.paymentMethod,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Amount and Date
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$amountPrefix${CurrencyFormatter.formatInr(transaction.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyFormatter.formatDateTime(transaction.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Reference ID or Self-transfer disclaimer pill
            if (transaction.referenceId.isNotBlank() || transaction.isTransferSelf) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (transaction.referenceId.isNotBlank()) {
                        Text(
                            text = "Ref: ${transaction.referenceId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (transaction.isTransferSelf) {
                        Surface(
                            color = SkyTransfer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Self-Transfer (Neutral)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = SkyTransfer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Custom Tags Pills Row
            if (transaction.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    transaction.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (tag.startsWith("#")) tag else "#$tag",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getCategoryVector(category: ExpenseCategory): ImageVector {
    return when (category) {
        ExpenseCategory.FOOD -> Icons.Default.Restaurant
        ExpenseCategory.GROCERIES -> Icons.Default.ShoppingCart
        ExpenseCategory.SHOPPING -> Icons.Default.ShoppingBag
        ExpenseCategory.TRAVEL -> Icons.Default.DirectionsCar
        ExpenseCategory.FUEL -> Icons.Default.LocalGasStation
        ExpenseCategory.BILLS -> Icons.Default.ReceiptLong
        ExpenseCategory.HEALTHCARE -> Icons.Default.MedicalServices
        ExpenseCategory.SUBSCRIPTIONS -> Icons.Default.Subscriptions
        ExpenseCategory.EMI -> Icons.Default.AccountBalance
        ExpenseCategory.RENT -> Icons.Default.Home
        ExpenseCategory.TRANSFERS -> Icons.Default.SwapHoriz
        ExpenseCategory.BUSINESS -> Icons.Default.BusinessCenter
        ExpenseCategory.SALARY -> Icons.Default.Payments
        ExpenseCategory.INVESTMENT -> Icons.Default.TrendingUp
        ExpenseCategory.OTHER -> Icons.Default.Category
    }
}
