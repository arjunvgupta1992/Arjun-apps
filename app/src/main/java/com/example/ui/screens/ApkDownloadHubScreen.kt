package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.data.repository.TransactionRepository
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SkyTransfer
import com.example.util.ExcelReportExporter
import com.example.util.ReportType

data class ApkHostingService(
    val name: String,
    val description: String,
    val linkFormat: String,
    val steps: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkDownloadHubScreen(
    transactionRepository: TransactionRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val transactions by transactionRepository.allApprovedTransactions.collectAsState(initial = emptyList())
    var selectedReportType by remember { mutableStateOf<ReportType?>(null) }

    val hostingList = remember {
        listOf(
            ApkHostingService(
                name = "1. GitHub Releases (Direct Download)",
                description = "Free, unlimited public hosting with version control and direct APK file URL.",
                linkFormat = "https://github.com/<username>/<repo>/releases/download/v1.0.0/MoneyLensAI-v1.0.0.apk",
                steps = "Create a new release on GitHub, upload 'app-debug.apk', and publish. The direct download URL follows this format."
            ),
            ApkHostingService(
                name = "2. Google Drive (Direct Download Link)",
                description = "Free Google Drive hosting with instant 1-tap direct download link without preview.",
                linkFormat = "https://drive.google.com/uc?export=download&id=<FILE_ID>",
                steps = "Upload APK to Google Drive, set sharing to 'Anyone with the link', copy the file ID from URL and paste into the direct link format."
            ),
            ApkHostingService(
                name = "3. Diawi (Free OTA 1-Click Install)",
                description = "Dedicated mobile app distribution with QR code and 1-tap browser installation.",
                linkFormat = "https://install.diawi.com/<unique_hash>",
                steps = "Visit diawi.com, drag & drop your APK. It instantly generates an installation link and scannable QR code for phones."
            ),
            ApkHostingService(
                name = "4. InstallOnAir (Free Android Tester Link)",
                description = "Free over-the-air APK distribution platform with instant web download.",
                linkFormat = "https://www.installonair.com/app-download-link/<token>",
                steps = "Go to installonair.com, upload your MoneyLens AI APK, and share the generated mobile download page."
            ),
            ApkHostingService(
                name = "5. Microsoft OneDrive (Direct Download Format)",
                description = "Direct APK download link without landing page redirect.",
                linkFormat = "https://onedrive.live.com/download?cid=<CID>&resid=<RESID>&authkey=<AUTHKEY>",
                steps = "Upload to OneDrive, click 'Embed' or copy link, and replace 'view' with 'download' for direct APK streaming."
            ),
            ApkHostingService(
                name = "6. Dropbox (Instant Direct Download)",
                description = "Direct download trigger using dl=1 parameter.",
                linkFormat = "https://www.dropbox.com/s/<share_id>/MoneyLensAI.apk?dl=1",
                steps = "Upload to Dropbox, generate share link, and change '?dl=0' to '?dl=1' to trigger immediate APK file download."
            ),
            ApkHostingService(
                name = "7. Firebase App Distribution",
                description = "Google Firebase's free developer distribution tool for Android APKs.",
                linkFormat = "https://appdistribution.firebase.google.com/testerapps/<project_id>",
                steps = "Enable App Distribution in Firebase Console, upload the APK, and generate invite download links for testers."
            ),
            ApkHostingService(
                name = "8. F-Droid / Custom FDN Web Repo",
                description = "Open source repository hosting via GitHub Pages.",
                linkFormat = "https://<username>.github.io/fdroid/repo/MoneyLensAI.apk",
                steps = "Deploy an F-Droid static directory to GitHub Pages to host an auto-updating APK repository."
            ),
            ApkHostingService(
                name = "9. Transfer.sh / WeTransfer (Direct Link)",
                description = "Instant temporary command-line or web download link.",
                linkFormat = "https://transfer.sh/MoneyLensAI.apk",
                steps = "Upload via web or run 'curl --upload-file app-debug.apk https://transfer.sh/MoneyLensAI.apk' to get a direct URL."
            ),
            ApkHostingService(
                name = "10. Internet Archive / APKMirror Community",
                description = "Permanent, free archive hosting with direct HTTP mirror links.",
                linkFormat = "https://archive.org/download/<item_id>/MoneyLensAI.apk",
                steps = "Upload to archive.org under Open Source Software to obtain permanent worldwide mirror download links."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("APK Download & Excel Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Distribution links & spreadsheet exports", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Excel Export Hub Section Header
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Excel & Spreadsheet Export Hub",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Export daily and weekly transfers, daily expenses, and monthly ledgers in Microsoft Excel (.csv) format with UTF-8 BOM encoding for clean rupee (₹) symbols and tabular formulas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportExportButton(
                                title = "1. Daily Expense Tracker Sheet",
                                subtitle = "Detailed daily expense items, merchants, categories & tags",
                                onClick = { ExcelReportExporter.generateAndShareExcelReport(context, ReportType.DAILY_EXPENSE, transactions) }
                            )
                            ReportExportButton(
                                title = "2. Weekly Spending Tracker Sheet",
                                subtitle = "7-day consolidated spending trend, daily averages & top merchants",
                                onClick = { ExcelReportExporter.generateAndShareExcelReport(context, ReportType.WEEKLY_EXPENSE, transactions) }
                            )
                            ReportExportButton(
                                title = "3. Monthly Financial Ledger Sheet",
                                subtitle = "Income vs expense ledger, savings rate & full monthly registry",
                                onClick = { ExcelReportExporter.generateAndShareExcelReport(context, ReportType.MONTHLY_EXPENSE, transactions) }
                            )
                            ReportExportButton(
                                title = "4. Daily & Weekly Transfer Log Sheet",
                                subtitle = "Dedicated log of account transfers & neutral self-transfers with UTR/Ref IDs",
                                onClick = { ExcelReportExporter.generateAndShareExcelReport(context, ReportType.TRANSFER_LOG, transactions) }
                            )
                        }
                    }
                }
            }

            // AI Studio Direct APK Export Header
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Studio Direct APK Download",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "In your Google AI Studio top bar or settings menu, click 'Download APK' or 'Export Project as ZIP'. You can immediately install 'app-debug.apk' on any Android phone or upload it to any of the 10 free hosting formats below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section Header: 10 Freely Available APK Hosting Links
            item {
                Text(
                    text = "10 Freely Available APK Download & Hosting Links",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(hostingList) { host ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = host.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("APK Link Format", host.linkFormat))
                                    Toast.makeText(context, "Copied link template!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link Format", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = host.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Monospace URL Format Box
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = host.linkFormat,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "How to host: ${host.steps}",
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
private fun ReportExportButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Download, contentDescription = "Download Excel CSV", tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
        }
    }
}
