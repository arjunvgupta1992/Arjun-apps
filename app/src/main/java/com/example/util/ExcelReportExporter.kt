package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportType(val title: String, val fileNamePrefix: String) {
    DAILY_EXPENSE("Daily Expense Tracker", "MoneyLens_Daily_Expense_Tracker"),
    WEEKLY_EXPENSE("Weekly Expense Tracker", "MoneyLens_Weekly_Expense_Tracker"),
    MONTHLY_EXPENSE("Monthly Financial Ledger", "MoneyLens_Monthly_Ledger"),
    TRANSFER_LOG("Daily & Weekly Transfer Log", "MoneyLens_Transfer_Log")
}

object ExcelReportExporter {

    /**
     * Generates an Excel-ready CSV spreadsheet with UTF-8 BOM so Microsoft Excel and
     * Google Sheets automatically render rupee symbols (₹) and formatted columns correctly.
     */
    fun generateAndShareExcelReport(
        context: Context,
        reportType: ReportType,
        transactions: List<TransactionEntity>
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "${reportType.fileNamePrefix}_$timeStamp.csv")

            FileOutputStream(file).use { fos ->
                // Write UTF-8 BOM (\uFEFF) so Excel opens UTF-8 files with correct currency and characters
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    when (reportType) {
                        ReportType.DAILY_EXPENSE -> writeDailyExpenseSheet(writer, transactions)
                        ReportType.WEEKLY_EXPENSE -> writeWeeklyExpenseSheet(writer, transactions)
                        ReportType.MONTHLY_EXPENSE -> writeMonthlyExpenseSheet(writer, transactions)
                        ReportType.TRANSFER_LOG -> writeTransferLogSheet(writer, transactions)
                    }
                }
            }

            // Launch system share chooser for Excel, Google Sheets, WhatsApp, Drive, etc.
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MoneyLens - ${reportType.title} ($timeStamp)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Open in Excel / Sheets or Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeDailyExpenseSheet(writer: OutputStreamWriter, transactions: List<TransactionEntity>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
            .sortedByDescending { it.timestamp }

        writer.append("=== MONEYLENS AI - DAILY EXPENSE TRACKER REPORT ===\n")
        writer.append("Generated on: ${dateFormat.format(Date())} ${timeFormat.format(Date())}\n")
        writer.append("Total Expense Entries: ${expenses.size}\n")
        writer.append("Total Outflow: ₹${String.format(Locale.ROOT, "%.2f", expenses.sumOf { it.amount })}\n\n")

        writer.append("Date,Time,Merchant / Payee,Category,Payment Mode,Amount (INR),Custom Tags,Account / Card,Reference ID,Notes\n")

        var grandTotal = 0.0
        for (t in expenses) {
            val dateStr = dateFormat.format(Date(t.timestamp))
            val timeStr = timeFormat.format(Date(t.timestamp))
            grandTotal += t.amount
            writer.append("$dateStr,$timeStr,${escapeCsv(t.merchant)},${escapeCsv(t.category)},${escapeCsv(t.paymentMethod)},${t.amount},${escapeCsv(t.tags)},${escapeCsv(t.account)},${escapeCsv(t.referenceId)},${escapeCsv(t.note)}\n")
        }

        writer.append("\n,,,TOTAL SPENT (INR),,₹${String.format(Locale.ROOT, "%.2f", grandTotal)},,,,\n\n")

        // Category Summary in Excel
        writer.append("=== CATEGORY BREAKDOWN ===\n")
        writer.append("Category,Number of Expenses,Total Amount (INR),Percentage of Total\n")
        expenses.groupBy { it.category }.forEach { (cat, list) ->
            val sum = list.sumOf { it.amount }
            val pct = if (grandTotal > 0) (sum / grandTotal * 100.0) else 0.0
            writer.append("${escapeCsv(cat)},${list.size},₹${String.format(Locale.ROOT, "%.2f", sum)},${String.format(Locale.ROOT, "%.1f", pct)}%\n")
        }
    }

    private fun writeWeeklyExpenseSheet(writer: OutputStreamWriter, transactions: List<TransactionEntity>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weekDayFormat = SimpleDateFormat("EEE (dd MMM)", Locale.getDefault())

        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }

        writer.append("=== MONEYLENS AI - WEEKLY SPENDING TRACKER REPORT ===\n")
        writer.append("Period: Last 7 Days (Consolidated Week)\n\n")

        writer.append("Day of Week,Date,Number of Expenses,Daily Total (INR),Average per Transaction (INR),Top Merchant\n")

        val cal = Calendar.getInstance()
        var weeklySum = 0.0

        for (i in 6 downTo 0) {
            val dCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startDay = dCal.timeInMillis
            val endDay = startDay + 86400000L - 1

            val dayExpenses = expenses.filter { it.timestamp in startDay..endDay }
            val dayTotal = dayExpenses.sumOf { it.amount }
            weeklySum += dayTotal
            val avg = if (dayExpenses.isNotEmpty()) dayTotal / dayExpenses.size else 0.0
            val topMerchant = dayExpenses.maxByOrNull { it.amount }?.merchant ?: "None"

            writer.append("${weekDayFormat.format(dCal.time)},${dateFormat.format(dCal.time)},${dayExpenses.size},₹${String.format(Locale.ROOT, "%.2f", dayTotal)},₹${String.format(Locale.ROOT, "%.2f", avg)},${escapeCsv(topMerchant)}\n")
        }

        writer.append("\nWEEKLY GRAND TOTAL (INR),,,₹${String.format(Locale.ROOT, "%.2f", weeklySum)},,\n")
        writer.append("DAILY AVERAGE SPEND (INR),,,₹${String.format(Locale.ROOT, "%.2f", weeklySum / 7.0)},,\n\n")

        // Detailed transaction log for the week
        writer.append("=== DETAILED TRANSACTIONS THIS WEEK ===\n")
        writer.append("Date,Merchant,Category,Amount (INR),Custom Tags,Payment Method,Notes\n")
        for (t in expenses.sortedByDescending { it.timestamp }) {
            writer.append("${dateFormat.format(Date(t.timestamp))},${escapeCsv(t.merchant)},${escapeCsv(t.category)},${t.amount},${escapeCsv(t.tags)},${escapeCsv(t.paymentMethod)},${escapeCsv(t.note)}\n")
        }
    }

    private fun writeMonthlyExpenseSheet(writer: OutputStreamWriter, transactions: List<TransactionEntity>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val income = transactions.filter { it.type == TransactionType.INCOME.name && !it.isTransferSelf }.sumOf { it.amount }
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name && !it.isTransferSelf }
        val expenseTotal = expenses.sumOf { it.amount }
        val netSavings = income - expenseTotal

        writer.append("=== MONEYLENS AI - MONTHLY FINANCIAL LEDGER ===\n")
        writer.append("Month: ${monthFormat.format(Date())}\n\n")

        writer.append("METRIC,VALUE (INR)\n")
        writer.append("Total Income Inflow,₹${String.format(Locale.ROOT, "%.2f", income)}\n")
        writer.append("Total Expenses Outflow,₹${String.format(Locale.ROOT, "%.2f", expenseTotal)}\n")
        writer.append("Net Monthly Savings,₹${String.format(Locale.ROOT, "%.2f", netSavings)}\n")
        writer.append("Savings Rate,${if (income > 0) String.format(Locale.ROOT, "%.1f", (netSavings / income) * 100) else "0"}%\n\n")

        writer.append("=== FULL MONTHLY TRANSACTION REGISTRY ===\n")
        writer.append("Date,Type,Merchant / Counterparty,Category,Amount (INR),Custom Tags,Payment Mode,Account,Reference ID,Notes\n")
        for (t in transactions.sortedByDescending { it.timestamp }) {
            writer.append("${dateFormat.format(Date(t.timestamp))},${t.type},${escapeCsv(t.merchant)},${escapeCsv(t.category)},${t.amount},${escapeCsv(t.tags)},${escapeCsv(t.paymentMethod)},${escapeCsv(t.account)},${escapeCsv(t.referenceId)},${escapeCsv(t.note)}\n")
        }
    }

    private fun writeTransferLogSheet(writer: OutputStreamWriter, transactions: List<TransactionEntity>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val transfers = transactions.filter { it.type == TransactionType.TRANSFER.name || it.isTransferSelf }
            .sortedByDescending { it.timestamp }

        writer.append("=== MONEYLENS AI - DAILY & WEEKLY ACCOUNT TRANSFER LOG ===\n")
        writer.append("Note: Transfers between own accounts are financially neutral and segregated from expenses.\n")
        writer.append("Total Recorded Transfers: ${transfers.size}\n")
        writer.append("Total Transfer Volume: ₹${String.format(Locale.ROOT, "%.2f", transfers.sumOf { it.amount })}\n\n")

        writer.append("Date,Time,Transfer Classification,From Account,To Account,Amount (INR),Custom Tags,UPI Ref / UTR,Status,Notes\n")

        var selfTransferVolume = 0.0
        var externalTransferVolume = 0.0

        for (t in transfers) {
            val dateStr = dateFormat.format(Date(t.timestamp))
            val timeStr = timeFormat.format(Date(t.timestamp))
            val classification = if (t.isTransferSelf) "Self-Transfer (Neutral)" else "Account Transfer"

            if (t.isTransferSelf) selfTransferVolume += t.amount else externalTransferVolume += t.amount

            writer.append("$dateStr,$timeStr,$classification,${escapeCsv(t.account)},${escapeCsv(t.toAccount ?: "Savings Account")},${t.amount},${escapeCsv(t.tags)},${escapeCsv(t.referenceId)},${t.status},${escapeCsv(t.note)}\n")
        }

        writer.append("\n=== TRANSFER VOLUME AGGREGATES ===\n")
        writer.append("Type,Total Volume (INR)\n")
        writer.append("Self-Transfers (Excluded from Expenses),₹${String.format(Locale.ROOT, "%.2f", selfTransferVolume)}\n")
        writer.append("External Transfers,₹${String.format(Locale.ROOT, "%.2f", externalTransferVolume)}\n")
        writer.append("Total Transferred Volume,₹${String.format(Locale.ROOT, "%.2f", selfTransferVolume + externalTransferVolume)}\n")
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
