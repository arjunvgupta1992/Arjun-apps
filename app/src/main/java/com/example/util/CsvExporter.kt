package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.TransactionEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun exportAndShareTransactions(
        context: Context,
        transactions: List<TransactionEntity>
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "MoneyLens_Transactions_$dateStr.csv")

            FileWriter(file).use { writer ->
                // Write CSV Header
                writer.append("ID,Date,Time,Type,Amount (INR),Category,Payment Method,Merchant,Account,Reference ID,Status,Is Self Transfer,Notes\n")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                for (t in transactions) {
                    val date = dateFormat.format(Date(t.timestamp))
                    val time = timeFormat.format(Date(t.timestamp))
                    val safeMerchant = escapeCsv(t.merchant)
                    val safeNotes = escapeCsv(t.note)
                    val safeAccount = escapeCsv(t.account)
                    val safeRef = escapeCsv(t.referenceId)

                    writer.append("${t.id},$date,$time,${t.type},${t.amount},${t.category},${t.paymentMethod},$safeMerchant,$safeAccount,$safeRef,${t.status},${t.isTransferSelf},$safeNotes\n")
                }
            }

            // Create share intent
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MoneyLens Financial Export - $dateStr")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Transactions CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
