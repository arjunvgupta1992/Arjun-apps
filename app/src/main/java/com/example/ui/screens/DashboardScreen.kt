package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.SpendingBarChart
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel
import com.example.util.CurrencyFormatter
import com.example.util.InsightType
import com.example.util.SpendingInsight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToUsage: () -> Unit,
    onNavigateToDataSource: () -> Unit,
    onNavigateToApkHub: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(EmeraldPrimary, EmeraldLight)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MoneyLens AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            // Live status badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (uiState.isMonitoringActive) EmeraldLight else AmberAccent)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isMonitoringActive) "Auto Sync Active" else "Monitoring Paused",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // APK & Excel Export Hub
                    IconButton(
                        onClick = onNavigateToApkHub,
                        modifier = Modifier.testTag("nav_apkhub_button")
                    ) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "APK & Excel Logs",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Queue badge button
                    IconButton(
                        onClick = onNavigateToQueue,
                        modifier = Modifier.testTag("nav_queue_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.pendingQueueCount > 0) {
                                    Badge(
                                        containerColor = AmberAccent,
                                        contentColor = Color.Black
                                    ) {
                                        Text("${uiState.pendingQueueCount}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = "Confirmation Queue",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Settings / Data Source
                    IconButton(
                        onClick = onNavigateToDataSource,
                        modifier = Modifier.testTag("nav_datasource_button")
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = "Data Source & Privacy",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddTransaction,
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_transaction_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Balance & Summary Hero Card
            item {
                BalanceHeroCard(
                    uiState = uiState,
                    onToggleVisibility = { viewModel.toggleBalanceVisibility() }
                )
            }

            // 2. Monthly Budget Progress Card
            item {
                MonthlyBudgetCard(
                    totalExpense = uiState.totalExpense,
                    budgetLimit = uiState.budget?.monthlyLimit ?: 35000.0,
                    progress = uiState.budgetProgressPercent
                )
            }

            // 3. Quick Action Bar
            item {
                QuickActionBar(
                    onAddClick = onNavigateToAddTransaction,
                    onImportClick = { showImportDialog = true },
                    onAnalyticsClick = onNavigateToAnalytics,
                    onUsageClick = onNavigateToUsage,
                    onApkHubClick = onNavigateToApkHub
                )
            }

            // 4. Seven-Day Spending Canvas Chart
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7-Day Spending Trend",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Last 7 Days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SpendingBarChart(
                            spendingList = uiState.sevenDaySpending,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. Category Breakdown Donut Chart
            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Monthly Spending by Category",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable(onClick = onNavigateToAnalytics)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            CategoryDonutChart(
                                categoryBreakdown = uiState.categoryBreakdown,
                                totalExpense = uiState.totalExpense
                            )
                        }
                    }
                }
            }

            // 6. AI Spending Lens Insights
            if (uiState.aiInsights.isNotEmpty()) {
                item {
                    AiInsightSection(insights = uiState.aiInsights)
                }
            }

            // 7. Search & Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search merchant, category, ref ID...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Filter chips row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("ALL" to "All", "EXPENSE" to "Expenses", "INCOME" to "Income", "TRANSFER" to "Transfers").forEach { (key, label) ->
                            FilterChip(
                                selected = uiState.selectedFilter == key,
                                onClick = { viewModel.setFilter(key) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("filter_chip_$key")
                            )
                        }
                    }

                    // Custom Tag Filter Chips Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Sell,
                            contentDescription = "Tag Filter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter by Tag:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedTag == "ALL",
                                onClick = { viewModel.setSelectedTag("ALL") },
                                label = { Text("All") },
                                modifier = Modifier.testTag("tag_filter_all")
                            )
                        }
                        val sampleAndDetectedTags = (uiState.availableTags + listOf("#vacation", "#work", "#family", "#trip", "#reimbursable", "#tax2026")).distinct()
                        items(sampleAndDetectedTags) { tag ->
                            val isSelected = uiState.selectedTag.equals(tag, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) viewModel.setSelectedTag("ALL") else viewModel.setSelectedTag(tag)
                                },
                                label = { Text(tag) },
                                modifier = Modifier.testTag("tag_filter_$tag")
                            )
                        }
                    }
                }
            }

            // 8. Recent Transactions Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.recentTransactions.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 9. Transaction Items
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    EmptyTransactionState(onAddClick = onNavigateToAddTransaction)
                }
            } else {
                items(uiState.recentTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        onClick = { onTransactionClick(tx) }
                    )
                }
            }

            // Bottom space for FAB padding
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    // Manual Paste / Import SMS Modal Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Transaction Alert") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a bank SMS or UPI notification text to test on-device parsing. Sensitive OTPs are strictly dropped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        placeholder = { Text("e.g. Sent Rs. 450.00 from HDFC Bank to SWIGGY on 28-OCT-26. UPI Ref 429384928349") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (importStatusMessage != null) {
                        Text(
                            text = importStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawInputText.isNotBlank()) {
                            viewModel.ingestRawMessage(rawInputText) { result ->
                                when (result) {
                                    is com.example.data.repository.IngestResult.Success -> {
                                        importStatusMessage = "Captured ₹${result.parsed.amount} at ${result.parsed.merchant} (${result.status})"
                                        rawInputText = ""
                                    }
                                    is com.example.data.repository.IngestResult.Ignored -> {
                                        importStatusMessage = "Skipped: ${result.reason}"
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Parse & Ingest")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BalanceHeroCard(
    uiState: DashboardUiState,
    onToggleVisibility: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESTIMATED BALANCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Balance Visibility",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (uiState.isBalanceVisible) CurrencyFormatter.formatInr(uiState.estimatedBalance) else "₹ •••••••",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Income
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmeraldLight)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Month Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isBalanceVisible) "+ ${CurrencyFormatter.formatInr(uiState.totalIncome)}" else "+ ₹••••",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldLight
                    )
                }

                // Total Expense
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CoralExpense)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Month Expenses",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isBalanceVisible) "- ${CurrencyFormatter.formatInr(uiState.totalExpense)}" else "- ₹••••",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CoralExpense
                    )
                }

                // Today
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Today's Spend",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isBalanceVisible) CurrencyFormatter.formatInr(uiState.todaySpending) else "₹•••",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyBudgetCard(
    totalExpense: Double,
    budgetLimit: Double,
    progress: Float
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Budget Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(progress * 100).toInt()}% Used",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 0.85f) CoralExpense else EmeraldLight
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (progress >= 0.85f) CoralExpense else EmeraldPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${CurrencyFormatter.formatInr(totalExpense)} spent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Limit: ${CurrencyFormatter.formatInr(budgetLimit)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActionBar(
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onUsageClick: () -> Unit,
    onApkHubClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.AddCircleOutline,
            label = "Add Entry",
            onClick = onAddClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.ContentPaste,
            label = "Paste SMS",
            onClick = onImportClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.TableChart,
            label = "Excel Logs",
            onClick = onApkHubClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.BarChart,
            label = "Analytics",
            onClick = onAnalyticsClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.Timer,
            label = "App Usage",
            onClick = onUsageClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AiInsightSection(insights: List<SpendingInsight>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI Lens Insights",
                    tint = AmberAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MoneyLens AI Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            insights.take(2).forEach { insight ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (insight.type) {
                                    InsightType.ALERT -> CoralExpense
                                    InsightType.WARNING -> AmberAccent
                                    InsightType.SUCCESS -> EmeraldLight
                                    InsightType.INFO -> MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = insight.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTransactionState(onAddClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.ReceiptLong,
                contentDescription = "No transactions",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Transactions Found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Transactions captured from bank alerts or added manually will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Your First Entry")
            }
        }
    }
}
