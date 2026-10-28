package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MoneyLensTheme
import com.example.ui.viewmodel.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MoneyLensApplication

        val dashboardViewModel by viewModels<DashboardViewModel> {
            DashboardViewModelFactory(app.transactionRepository, app.budgetRepository, app.preferencesManager)
        }
        val queueViewModel by viewModels<ConfirmationQueueViewModel> {
            ConfirmationQueueViewModelFactory(app.transactionRepository)
        }
        val addEditViewModel by viewModels<AddEditTransactionViewModel> {
            AddEditTransactionViewModelFactory(app.transactionRepository)
        }
        val analyticsViewModel by viewModels<AnalyticsViewModel> {
            AnalyticsViewModelFactory(app.transactionRepository)
        }
        val usageStatsViewModel by viewModels<UsageStatsViewModel> {
            UsageStatsViewModelFactory(app.usageStatsRepository)
        }
        val dataSourceViewModel by viewModels<DataSourceViewModel> {
            DataSourceViewModelFactory(app.preferencesManager, app.transactionRepository, app.usageStatsRepository)
        }

        val navigateToQueueFromNotification = intent?.getStringExtra("NAVIGATE_TO") == "QUEUE"

        setContent {
            MoneyLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val isOnboardingCompleted by app.preferencesManager.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = true)

                    val startDestination = when {
                        !isOnboardingCompleted -> "onboarding"
                        navigateToQueueFromNotification -> "confirmation_queue"
                        else -> "dashboard"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("onboarding") {
                            OnboardingScreen(
                                preferencesManager = app.preferencesManager,
                                onComplete = {
                                    navController.navigate("dashboard") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToAddTransaction = {
                                    navController.navigate("add_transaction")
                                },
                                onNavigateToQueue = {
                                    navController.navigate("confirmation_queue")
                                },
                                onNavigateToAnalytics = {
                                    navController.navigate("analytics")
                                },
                                onNavigateToUsage = {
                                    navController.navigate("app_usage")
                                },
                                onNavigateToDataSource = {
                                    navController.navigate("data_source")
                                },
                                onNavigateToApkHub = {
                                    navController.navigate("apk_hub")
                                },
                                onTransactionClick = { tx ->
                                    navController.navigate("edit_transaction/${tx.id}")
                                }
                            )
                        }

                        composable("add_transaction") {
                            AddEditTransactionScreen(
                                viewModel = addEditViewModel,
                                transactionId = null,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "edit_transaction/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getLong("id")
                            AddEditTransactionScreen(
                                viewModel = addEditViewModel,
                                transactionId = id,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("confirmation_queue") {
                            ConfirmationQueueScreen(
                                viewModel = queueViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onEditTransaction = { id ->
                                    navController.navigate("edit_transaction/$id")
                                }
                            )
                        }

                        composable("analytics") {
                            AnalyticsScreen(
                                viewModel = analyticsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("app_usage") {
                            AppUsageScreen(
                                viewModel = usageStatsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("data_source") {
                            DataSourceStatusScreen(
                                viewModel = dataSourceViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToRulesReminders = {
                                    navController.navigate("rules_reminders")
                                }
                            )
                        }

                        composable("rules_reminders") {
                            RulesRemindersScreen(
                                budgetRepository = app.budgetRepository,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("apk_hub") {
                            ApkDownloadHubScreen(
                                transactionRepository = app.transactionRepository,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
