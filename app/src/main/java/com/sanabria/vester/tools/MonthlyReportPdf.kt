// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sanabria.vester.R
import com.sanabria.vester.formatDate
import com.sanabria.vester.formatMoneyExact
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.room.Transaction
import java.io.OutputStream
import kotlin.math.abs

/**
 * Informe de un mes en PDF (A4): ingresos, gastos y balance, el gasto por categoría con sus barras y la lista de
 * movimientos. Se dibuja directamente con [PdfDocument], sin librerías. Las transferencias no cuentan como gasto.
 */
object MonthlyReportPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW = 18f

    private val ink = Color.rgb(0x1F, 0x1A, 0x14)
    private val muted = Color.rgb(0x5F, 0x57, 0x4D)
    private val green = Color.rgb(0x17, 0x7A, 0x33)
    private val red = Color.rgb(0xD3, 0x2F, 0x2F)
    private val accent = Color.rgb(0xC4, 0x5F, 0x00)

    private fun paint(size: Float, color: Int = ink, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = align
    }

    fun write(
        context: Context, out: OutputStream, monthTitle: String,
        transactions: List<Transaction>, accounts: List<Account>, categories: List<Category>,
    ) {
        val accountById = accounts.associateBy { it.accountId }
        val categoryById = categories.associateBy { it.categoryId }
        val real = transactions.filter { !it.isTransfer }
        val income = real.filter { it.value > 0 }.sumOf { it.value }
        val expense = real.filter { it.value < 0 }.sumOf { it.value }
        val byCategory = real.filter { it.value < 0 }.groupBy { it.categoryId }
            .map { (id, list) -> (categoryById[id]?.label ?: context.getString(R.string.uncategorized)) to -list.sumOf { it.value } }
            .sortedByDescending { it.second }

        val document = PdfDocument()
        var page = 0
        var pdfPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun startPage() {
            pdfPage?.let { document.finishPage(it) }
            page++
            pdfPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, page).create())
            canvas = pdfPage!!.canvas
            y = MARGIN + 14
            canvas!!.drawText("vESTER · $monthTitle", MARGIN, y, paint(11f, muted))
            canvas!!.drawText(page.toString(), PAGE_WIDTH - MARGIN, y, paint(11f, muted, align = Paint.Align.RIGHT))
            y += 24
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN) startPage()
        }

        startPage()
        canvas!!.drawText(monthTitle, MARGIN, y + 8, paint(24f, ink, bold = true))
        y += 40

        // Resumen
        val summary = listOf(
            context.getString(R.string.income) to income,
            context.getString(R.string.expenses) to expense,
            context.getString(R.string.balance) to income + expense,
        )
        val columnWidth = (PAGE_WIDTH - 2 * MARGIN) / summary.size
        summary.forEachIndexed { index, (label, value) ->
            val x = MARGIN + index * columnWidth
            canvas!!.drawText(label, x, y, paint(11f, muted))
            canvas!!.drawText(formatMoneyExact(value), x, y + 22, paint(17f, if (value < 0) red else if (index == 0) green else ink, bold = true))
        }
        y += 52

        // Gasto por categoría
        if (byCategory.isNotEmpty()) {
            canvas!!.drawText(context.getString(R.string.report_by_category), MARGIN, y, paint(14f, accent, bold = true))
            y += 10
            val max = byCategory.first().second.toFloat().coerceAtLeast(1f)
            val barLeft = MARGIN + 190
            val barRight = PAGE_WIDTH - MARGIN - 90
            for ((name, amount) in byCategory) {
                ensure(ROW + 6)
                y += ROW + 2
                canvas!!.drawText(name.take(28), MARGIN, y, paint(11f))
                val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas!!.drawRoundRect(barLeft, y - 9, barLeft + (barRight - barLeft) * amount / max, y + 1, 3f, 3f, bar)
                canvas!!.drawText(formatMoneyExact(amount), PAGE_WIDTH - MARGIN, y, paint(11f, ink, align = Paint.Align.RIGHT))
            }
            y += 24
        }

        // Movimientos
        ensure(60f)
        canvas!!.drawText(context.getString(R.string.all_transactions_short), MARGIN, y, paint(14f, accent, bold = true))
        y += 6
        val textPaint = paint(10f)
        for (transaction in transactions) {
            ensure(ROW + 4)
            y += ROW
            val account = accountById[transaction.accountId]?.name.orEmpty()
            val category = categoryById[transaction.categoryId]?.name.orEmpty()
            canvas!!.drawText(formatDate(transaction.date), MARGIN, y, textPaint)
            canvas!!.drawText(transaction.concept.take(34), MARGIN + 80, y, textPaint)
            canvas!!.drawText(listOf(account, category).filter { it.isNotEmpty() }.joinToString(" · ").take(34), MARGIN + 280, y, paint(9f, muted))
            val color = when { transaction.isTransfer -> muted; transaction.value < 0 -> red; else -> green }
            canvas!!.drawText(formatMoneyExact(transaction.value), PAGE_WIDTH - MARGIN, y, paint(10f, color, align = Paint.Align.RIGHT))
        }
        if (transactions.isEmpty()) {
            y += ROW
            canvas!!.drawText(context.getString(R.string.no_transactions), MARGIN, y, paint(11f, muted))
        }
        // Total de movimientos al final
        ensure(40f)
        y += 30
        canvas!!.drawText(context.getString(R.string.report_count, transactions.size, formatMoneyExact(abs(expense))), MARGIN, y, paint(10f, muted))

        document.finishPage(pdfPage!!)
        document.writeTo(out)
        document.close()
    }
}
