package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionType
import com.example.ui.components.getCategoryVector
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SkyTransfer
import com.example.ui.viewmodel.AddEditTransactionViewModel
import com.example.util.CurrencyFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionScreen(
    viewModel: AddEditTransactionViewModel,
    transactionId: Long? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(transactionId) {
        if (transactionId != null && transactionId > 0) {
            viewModel.loadTransaction(transactionId)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.transactionId != null) "Edit Transaction" else "Add Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Transaction Type Selector Tabs (Expense, Income, Transfer)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.type == TransactionType.EXPENSE,
                    onClick = { viewModel.updateType(TransactionType.EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Expense")
                }
                SegmentedButton(
                    selected = uiState.type == TransactionType.INCOME,
                    onClick = { viewModel.updateType(TransactionType.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("Income")
                }
                SegmentedButton(
                    selected = uiState.type == TransactionType.TRANSFER,
                    onClick = { viewModel.updateType(TransactionType.TRANSFER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Transfer")
                }
            }

            // Self-transfer neutral disclosure if transfer is selected
            if (uiState.type == TransactionType.TRANSFER) {
                Surface(
                    color = SkyTransfer.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uiState.isTransferSelf,
                            onCheckedChange = { viewModel.updateIsTransferSelf(it) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Transfer Between My Own Accounts",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Excluded from total income and total expenses so it doesn't inflate your spending stats.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Amount Input
            OutlinedTextField(
                value = uiState.amountText,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("Amount") },
                leadingIcon = {
                    Text(
                        "₹",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = uiState.errorMessage != null,
                supportingText = {
                    if (uiState.errorMessage != null) {
                        Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // 3. Merchant / Payee
            OutlinedTextField(
                value = uiState.merchant,
                onValueChange = { viewModel.updateMerchant(it) },
                label = { Text(if (uiState.type == TransactionType.INCOME) "Payer / Source" else "Merchant / Payee") },
                placeholder = { Text("e.g. Swiggy, Amazon, Salary, Chai Point") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merchant_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // 4. Category Selector (Horizontal scrollable chips with icons)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    val categories = ExpenseCategory.entries.filter {
                        if (uiState.type == TransactionType.INCOME) {
                            it in listOf(ExpenseCategory.SALARY, ExpenseCategory.INVESTMENT, ExpenseCategory.BUSINESS, ExpenseCategory.OTHER)
                        } else if (uiState.type == TransactionType.TRANSFER) {
                            it in listOf(ExpenseCategory.TRANSFERS, ExpenseCategory.OTHER)
                        } else {
                            it !in listOf(ExpenseCategory.SALARY, ExpenseCategory.INVESTMENT)
                        }
                    }
                    items(categories) { cat ->
                        val isSelected = uiState.category == cat.displayName
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateCategory(cat.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getCategoryVector(cat),
                                    contentDescription = cat.displayName,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(cat.colorHex)
                                )
                            },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
            }

            // 5. Payment Method Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PaymentMethod.entries) { method ->
                        FilterChip(
                            selected = uiState.paymentMethod == method.displayName,
                            onClick = { viewModel.updatePaymentMethod(method.displayName) },
                            label = { Text(method.displayName) }
                        )
                    }
                }
            }

            // 6. Source Account (and Destination Account for Transfers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.account,
                    onValueChange = { viewModel.updateAccount(it) },
                    label = { Text(if (uiState.type == TransactionType.TRANSFER) "From Account" else "Account / Card") },
                    placeholder = { Text("e.g. HDFC A/c") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                if (uiState.type == TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = uiState.toAccount,
                        onValueChange = { viewModel.updateToAccount(it) },
                        label = { Text("To Account") },
                        placeholder = { Text("e.g. SBI Savings") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 7. Date and Time Pickers
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Date & Time",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatDateTime(uiState.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = uiState.timestamp }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        cal.set(Calendar.YEAR, year)
                                        cal.set(Calendar.MONTH, month)
                                        cal.set(Calendar.DAY_OF_MONTH, day)
                                        viewModel.updateTimestamp(cal.timeInMillis)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Change Date")
                        }

                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = uiState.timestamp }
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hour)
                                        cal.set(Calendar.MINUTE, minute)
                                        viewModel.updateTimestamp(cal.timeInMillis)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Time")
                        }
                    }
                }
            }

            // 8. Reference ID (UPI Ref / UTR / Txn ID)
            OutlinedTextField(
                value = uiState.referenceId,
                onValueChange = { viewModel.updateReferenceId(it) },
                label = { Text("Reference ID / UPI Ref / UTR (Optional)") },
                placeholder = { Text("e.g. 429384928349") },
                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reference_id_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // 9. Custom Tags Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Custom Tags (#labels)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = uiState.tagsText,
                    onValueChange = { viewModel.updateTags(it) },
                    placeholder = { Text("e.g. #vacation, #work, #reimbursable") },
                    leadingIcon = { Icon(Icons.Default.Sell, contentDescription = "Tags") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tags_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick Tag Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    val sampleTags = listOf("#work", "#vacation", "#family", "#trip", "#personal", "#tax2026", "#reimbursable", "#gift", "#medical")
                    items(sampleTags) { tag ->
                        val isSelected = uiState.tagsText.contains(tag, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // 10. Notes
            OutlinedTextField(
                value = uiState.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("e.g. Dinner with clients, hotel booking") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveTransaction() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_transaction_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.transactionId != null) "Update Transaction" else "Save Transaction",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
