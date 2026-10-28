package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.ExpenseTransaction
import com.example.data.MonthBudgetConfig
import com.example.data.MonthFinancialSummary
import com.example.data.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ExportManager {

    enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
        PDF(".pdf", "application/pdf", "PDF Statement"),
        EXCEL(".xls", "application/vnd.ms-excel", "Excel (.xls)"),
        CSV(".csv", "text/csv", "CSV Spreadsheet"),
        SHEET(".csv", "text/csv", "Google Sheets (.csv)")
    }

    private fun getReportsDir(context: Context): File {
        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportTransactions(
        context: Context,
        format: ExportFormat,
        monthKey: String,
        summary: MonthFinancialSummary?,
        transactions: List<ExpenseTransaction>
    ): Pair<File, Uri> {
        val dir = getReportsDir(context)
        val fileName = "CashTrace_${monthKey}_Report${format.extension}"
        val file = File(dir, fileName)

        when (format) {
            ExportFormat.PDF -> generatePdf(file, monthKey, summary, transactions)
            ExportFormat.EXCEL -> generateExcel(file, monthKey, summary, transactions)
            ExportFormat.CSV -> generateCsv(file, monthKey, summary, transactions)
            ExportFormat.SHEET -> generateGoogleSheetsCsv(file, monthKey, summary, transactions)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    fun shareExportedFile(context: Context, file: File, uri: Uri, mimeType: String, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            clipData = android.content.ClipData.newRawUri(title, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Here is your $title from Cash Trace Expense Manager.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Grant URI read permission to all matching activities
        val packageManager = context.packageManager
        val resInfoList = packageManager.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share with Google Sheets, Drive or...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    private fun generatePdf(
        file: File,
        monthKey: String,
        summary: MonthFinancialSummary?,
        transactions: List<ExpenseTransaction>
    ) {
        val document = PdfDocument()
        val pageWidth = 595 // standard A4
        val pageHeight = 842
        var pageNumber = 1

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.BLACK
        }

        // Header Background
        paint.color = Color.rgb(15, 61, 57) // Deep teal #0F3D39
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, paint)

        // Title
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Cash Trace - Expense Statement", 30f, 42f, paint)

        // Subtitle
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(220, 240, 235)
        val displayMonth = DateUtils.formatDisplayMonth(monthKey)
        canvas.drawText("Period: $displayMonth  |  Generated on ${DateUtils.formatShortDate(System.currentTimeMillis())}", 30f, 65f, paint)

        var y = 120f

        // Summary Card
        if (summary != null) {
            paint.color = Color.rgb(240, 247, 245)
            canvas.drawRoundRect(25f, y, (pageWidth - 25).toFloat(), y + 80f, 8f, 8f, paint)

            paint.color = Color.rgb(15, 61, 57)
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            canvas.drawText("Start Balance: ${CurrencyFormatter.formatInr(summary.startingBalance)}", 40f, y + 25f, paint)
            canvas.drawText("Rolled Over: ${CurrencyFormatter.formatInr(summary.rolledOverAmount)}", 40f, y + 50f, paint)

            paint.color = Color.rgb(0, 168, 107) // Green
            canvas.drawText("Total Credits (+): ${CurrencyFormatter.formatInr(summary.totalIncome)}", 210f, y + 25f, paint)
            paint.color = Color.rgb(211, 47, 47) // Red
            canvas.drawText("Total Debits (-): ${CurrencyFormatter.formatInr(summary.totalExpense)}", 210f, y + 50f, paint)

            paint.color = Color.rgb(11, 25, 44)
            canvas.drawText("Net Balance: ${CurrencyFormatter.formatInr(summary.netBalance)}", 390f, y + 25f, paint)
            paint.color = if (summary.budgetRemaining >= 0) Color.rgb(0, 168, 107) else Color.rgb(211, 47, 47)
            canvas.drawText("Budget Left: ${CurrencyFormatter.formatInr(summary.budgetRemaining)}", 390f, y + 50f, paint)

            y += 105f
        }

        // Table Header
        paint.color = Color.rgb(225, 232, 237)
        canvas.drawRect(25f, y, (pageWidth - 25).toFloat(), y + 24f, paint)

        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("Date", 32f, y + 16f, paint)
        canvas.drawText("Description", 100f, y + 16f, paint)
        canvas.drawText("Category", 250f, y + 16f, paint)
        canvas.drawText("Mode", 365f, y + 16f, paint)
        canvas.drawText("Type", 435f, y + 16f, paint)
        canvas.drawText("Amount (INR)", 495f, y + 16f, paint)

        y += 28f
        paint.typeface = Typeface.DEFAULT

        // Table Rows
        for (tx in transactions) {
            if (y > pageHeight - 50) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }

            // Zebra background
            if (transactions.indexOf(tx) % 2 == 0) {
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(25f, y - 12f, (pageWidth - 25).toFloat(), y + 8f, paint)
            }

            paint.textSize = 8.5f
            paint.color = Color.rgb(51, 65, 85)

            val dateStr = DateUtils.formatShortDate(tx.timestamp)
            canvas.drawText(dateStr, 32f, y, paint)

            val titleClipped = if (tx.title.length > 25) tx.title.take(22) + "..." else tx.title
            canvas.drawText(titleClipped, 100f, y, paint)

            val catClipped = if (tx.category.length > 18) tx.category.take(16) + ".." else tx.category
            canvas.drawText(catClipped, 250f, y, paint)

            canvas.drawText(tx.paymentMode.displayName, 365f, y, paint)

            if (tx.type == TransactionType.INCOME) {
                paint.color = Color.rgb(0, 150, 80)
                canvas.drawText("CREDIT", 435f, y, paint)
                canvas.drawText("+${CurrencyFormatter.formatInr(tx.amount)}", 495f, y, paint)
            } else {
                paint.color = Color.rgb(210, 40, 40)
                canvas.drawText("DEBIT", 435f, y, paint)
                canvas.drawText("-${CurrencyFormatter.formatInr(tx.amount)}", 495f, y, paint)
            }

            y += 20f
        }

        // Footer
        paint.color = Color.GRAY
        paint.textSize = 8f
        canvas.drawText("Page $pageNumber  •  Generated by Cash Trace Financial Assistant", 30f, (pageHeight - 20).toFloat(), paint)

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun generateCsv(
        file: File,
        monthKey: String,
        summary: MonthFinancialSummary?,
        transactions: List<ExpenseTransaction>
    ) {
        OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
            writer.write("sep=,\n") // Hint for Excel
            writer.write("\"Cash Trace Financial Report - ${DateUtils.formatDisplayMonth(monthKey)}\"\n")
            if (summary != null) {
                writer.write("\"Starting Balance\",${summary.startingBalance}\n")
                writer.write("\"Rolled Over from Previous\",${summary.rolledOverAmount}\n")
                writer.write("\"Total Credits\",${summary.totalIncome}\n")
                writer.write("\"Total Debits\",${summary.totalExpense}\n")
                writer.write("\"Net Balance\",${summary.netBalance}\n")
                writer.write("\"Monthly Budget Limit\",${summary.budgetLimit}\n")
                writer.write("\"Budget Remaining\",${summary.budgetRemaining}\n\n")
            }

            writer.write("\"Date\",\"Time\",\"Type\",\"Title\",\"Category\",\"Payment Mode\",\"Amount (INR)\",\"Notes\",\"Source\"\n")
            for (tx in transactions) {
                val dateStr = DateUtils.formatShortDate(tx.timestamp)
                val timeStr = DateUtils.formatTime(tx.timestamp)
                val typeStr = if (tx.type == TransactionType.INCOME) "CREDIT" else "DEBIT"
                val amountSigned = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount

                val cleanTitle = tx.title.replace("\"", "\"\"")
                val cleanCategory = tx.category.replace("\"", "\"\"")
                val cleanNote = tx.note.replace("\"", "\"\"")

                writer.write("\"$dateStr\",\"$timeStr\",\"$typeStr\",\"$cleanTitle\",\"$cleanCategory\",\"${tx.paymentMode.displayName}\",$amountSigned,\"$cleanNote\",\"${tx.sourceTag}\"\n")
            }
        }
    }

    private fun generateGoogleSheetsCsv(
        file: File,
        monthKey: String,
        summary: MonthFinancialSummary?,
        transactions: List<ExpenseTransaction>
    ) {
        // Formatted specifically for Google Sheets (RFC 4180 standard, UTF-8 with BOM, clean table)
        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM so Google Sheets directly detects UTF-8 characters
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Header row for Google Sheets
                writer.write("\"Date\",\"Time\",\"Type\",\"Title / Description\",\"Category\",\"Payment Mode\",\"Amount (INR)\",\"Notes\",\"Logged Channel\"\n")
                for (tx in transactions) {
                    val dateStr = DateUtils.formatShortDate(tx.timestamp)
                    val timeStr = DateUtils.formatTime(tx.timestamp)
                    val typeStr = if (tx.type == TransactionType.INCOME) "Income (Credit)" else "Expense (Debit)"
                    val amountVal = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount

                    val cleanTitle = tx.title.replace("\"", "\"\"")
                    val cleanCategory = tx.category.replace("\"", "\"\"")
                    val cleanNote = tx.note.replace("\"", "\"\"")

                    writer.write("\"$dateStr\",\"$timeStr\",\"$typeStr\",\"$cleanTitle\",\"$cleanCategory\",\"${tx.paymentMode.displayName}\",$amountVal,\"$cleanNote\",\"${tx.sourceTag}\"\n")
                }
            }
        }
    }

    private fun generateExcel(
        file: File,
        monthKey: String,
        summary: MonthFinancialSummary?,
        transactions: List<ExpenseTransaction>
    ) {
        // Generates Microsoft Excel XML Spreadsheet (SpreadsheetML) compatible with MS Excel, LibreOffice, and Google Sheets
        OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.write("<?mso-application progid=\"Excel.Sheet\"?>\n")
            writer.write("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
            writer.write(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n")
            writer.write(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n")
            writer.write(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")

            // Styles
            writer.write(" <Styles>\n")
            writer.write("  <Style ss:ID=\"Header\">\n")
            writer.write("   <Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\" ss:Size=\"12\"/>\n")
            writer.write("   <Interior ss:Color=\"#0F3D39\" ss:Pattern=\"Solid\"/>\n")
            writer.write("  </Style>\n")
            writer.write("  <Style ss:ID=\"Credit\">\n")
            writer.write("   <Font ss:Color=\"#008744\" ss:Bold=\"1\"/>\n")
            writer.write("  </Style>\n")
            writer.write("  <Style ss:ID=\"Debit\">\n")
            writer.write("   <Font ss:Color=\"#D62D20\" ss:Bold=\"1\"/>\n")
            writer.write("  </Style>\n")
            writer.write("  <Style ss:ID=\"Bold\">\n")
            writer.write("   <Font ss:Bold=\"1\"/>\n")
            writer.write("  </Style>\n")
            writer.write(" </Styles>\n")

            writer.write(" <Worksheet ss:Name=\"${monthKey}\">\n")
            writer.write("  <Table>\n")

            // Summary Section
            writer.write("   <Row><Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">Cash Trace Financial Report - ${DateUtils.formatDisplayMonth(monthKey)}</Data></Cell></Row>\n")
            if (summary != null) {
                writer.write("   <Row><Cell><Data ss:Type=\"String\">Starting Balance</Data></Cell><Cell><Data ss:Type=\"Number\">${summary.startingBalance}</Data></Cell></Row>\n")
                writer.write("   <Row><Cell><Data ss:Type=\"String\">Rolled Over Money</Data></Cell><Cell><Data ss:Type=\"Number\">${summary.rolledOverAmount}</Data></Cell></Row>\n")
                writer.write("   <Row><Cell><Data ss:Type=\"String\">Total Income (Credits)</Data></Cell><Cell ss:StyleID=\"Credit\"><Data ss:Type=\"Number\">${summary.totalIncome}</Data></Cell></Row>\n")
                writer.write("   <Row><Cell><Data ss:Type=\"String\">Total Expenses (Debits)</Data></Cell><Cell ss:StyleID=\"Debit\"><Data ss:Type=\"Number\">${summary.totalExpense}</Data></Cell></Row>\n")
                writer.write("   <Row><Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">Net Balance</Data></Cell><Cell ss:StyleID=\"Bold\"><Data ss:Type=\"Number\">${summary.netBalance}</Data></Cell></Row>\n")
                writer.write("   <Row><Cell><Data ss:Type=\"String\">Monthly Budget Limit</Data></Cell><Cell><Data ss:Type=\"Number\">${summary.budgetLimit}</Data></Cell></Row>\n")
                writer.write("   <Row><Cell><Data ss:Type=\"String\">Remaining Budget</Data></Cell><Cell><Data ss:Type=\"Number\">${summary.budgetRemaining}</Data></Cell></Row>\n")
                writer.write("   <Row></Row>\n")
            }

            // Headers
            writer.write("   <Row ss:StyleID=\"Header\">\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Date</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Time</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Type</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Title</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Category</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Payment Mode</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Amount (₹)</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Notes</Data></Cell>\n")
            writer.write("    <Cell><Data ss:Type=\"String\">Input Channel</Data></Cell>\n")
            writer.write("   </Row>\n")

            // Rows
            for (tx in transactions) {
                val dateStr = DateUtils.formatShortDate(tx.timestamp)
                val timeStr = DateUtils.formatTime(tx.timestamp)
                val typeStyle = if (tx.type == TransactionType.INCOME) "Credit" else "Debit"
                val typeName = if (tx.type == TransactionType.INCOME) "CREDIT" else "DEBIT"

                writer.write("   <Row>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">$dateStr</Data></Cell>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">$timeStr</Data></Cell>\n")
                writer.write("    <Cell ss:StyleID=\"$typeStyle\"><Data ss:Type=\"String\">$typeName</Data></Cell>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.title)}</Data></Cell>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.category)}</Data></Cell>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">${tx.paymentMode.displayName}</Data></Cell>\n")
                writer.write("    <Cell ss:StyleID=\"$typeStyle\"><Data ss:Type=\"Number\">${tx.amount}</Data></Cell>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">${escapeXml(tx.note)}</Data></Cell>\n")
                writer.write("    <Cell><Data ss:Type=\"String\">${tx.sourceTag}</Data></Cell>\n")
                writer.write("   </Row>\n")
            }

            writer.write("  </Table>\n")
            writer.write(" </Worksheet>\n")
            writer.write("</Workbook>\n")
        }
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
