package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryRuleEntity
import com.example.data.model.ExpenseCategory
import com.example.data.model.RecurringReminderEntity
import com.example.data.repository.BudgetRepository
import com.example.ui.theme.EmeraldPrimary
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesRemindersScreen(
    budgetRepository: BudgetRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val rules by budgetRepository.categoryRules.collectAsState(initial = emptyList())
    val reminders by budgetRepository.recurringReminders.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0 = Rules, 1 = Reminders
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }

    // Add Rule states
    var newKeyword by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(ExpenseCategory.FOOD.displayName) }

    // Add Reminder states
    var reminderTitle by remember { mutableStateOf("") }
    var reminderAmount by remember { mutableStateOf("") }
    var reminderDay by remember { mutableStateOf("5") }

    val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rules & Recurring Bills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddRuleDialog = true else showAddReminderDialog = true
                },
                containerColor = EmeraldPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Category Rules (${rules.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Recurring Bills (${reminders.size})") }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "When an incoming transaction alert contains any of these merchant keywords, it is automatically categorized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(rules, key = { it.id }) { rule ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                        text = rule.keyword.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Maps to ${rule.targetCategory}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            budgetRepository.deleteCategoryRule(rule.id)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "Track upcoming monthly subscriptions, utility bills, and EMIs to avoid late fees.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(reminders, key = { it.id }) { rem ->
                        val isPaidThisMonth = rem.lastPaidMonthYear == currentMonthYear
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rem.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Due on ${rem.dueDayOfMonth}th of month • ${rem.category}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatInr(rem.expectedAmount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isPaidThisMonth) {
                                        Surface(
                                            color = EmeraldPrimary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Paid",
                                                color = EmeraldPrimary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    budgetRepository.markReminderPaid(rem.id, currentMonthYear)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Mark Paid")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Rule Dialog
    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Add Category Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("Merchant Keyword") },
                        placeholder = { Text("e.g. Starbucks, Cult.fit, D-Mart") },
                        singleLine = true
                    )
                    Text("Select Category:", style = MaterialTheme.typography.labelSmall)
                    // Simple drop-down / chip selector
                    ExpenseCategory.entries.take(8).forEach { cat ->
                        FilterChip(
                            selected = newCategory == cat.displayName,
                            onClick = { newCategory = cat.displayName },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            coroutineScope.launch {
                                budgetRepository.addCategoryRule(newKeyword, newCategory)
                                newKeyword = ""
                                showAddRuleDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Reminder Dialog
    if (showAddReminderDialog) {
        AlertDialog(
            onDismissRequest = { showAddReminderDialog = false },
            title = { Text("Add Bill Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        label = { Text("Title") },
                        placeholder = { Text("e.g. Home Rent, Bescom Bill") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reminderAmount,
                        onValueChange = { reminderAmount = it },
                        label = { Text("Expected Amount (₹)") },
                        placeholder = { Text("e.g. 15000") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reminderDay,
                        onValueChange = { reminderDay = it },
                        label = { Text("Due Day of Month (1-31)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = reminderAmount.toDoubleOrNull() ?: 0.0
                        val day = reminderDay.toIntOrNull()?.coerceIn(1, 31) ?: 1
                        if (reminderTitle.isNotBlank() && amount > 0) {
                            coroutineScope.launch {
                                budgetRepository.addRecurringReminder(
                                    RecurringReminderEntity(
                                        title = reminderTitle.trim(),
                                        expectedAmount = amount,
                                        category = ExpenseCategory.BILLS.displayName,
                                        dueDayOfMonth = day
                                    )
                                )
                                reminderTitle = ""
                                reminderAmount = ""
                                showAddReminderDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save Reminder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminderDialog = false }) { Text("Cancel") }
            }
        )
    }
}
