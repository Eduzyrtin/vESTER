// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sanabria.vester.R
import com.sanabria.vester.datamanager.debtName
import com.sanabria.vester.formatDate
import com.sanabria.vester.formatMoneyExact
import com.sanabria.vester.room.Debt
import com.sanabria.vester.room.DebtPayment
import java.io.OutputStream
import kotlin.math.abs

/** Resumen de una deuda en PDF (una página A4, o más si hay muchos pagos): total, pagado, lo que queda y cada pago. */
object DebtReportPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f

    private fun paint(size: Float, color: Int = Color.rgb(0x1F, 0x1A, 0x14), bold: Boolean = false, right: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        }

    fun write(context: Context, out: OutputStream, debt: Debt, payments: List<DebtPayment>) {
        val document = PdfDocument()
        var number = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
        var y = MARGIN + 20
        val muted = Color.rgb(0x5F, 0x57, 0x4D)
        val total = abs(debt.total)
        fun line(label: String, value: String, bold: Boolean = false) {
            page.canvas.drawText(label, MARGIN, y, paint(12f, muted))
            page.canvas.drawText(value, WIDTH - MARGIN, y, paint(12f, bold = bold, right = true))
            y += 22
        }
        page.canvas.drawText(debtName(debt), MARGIN, y, paint(24f, bold = true))
        y += 24
        page.canvas.drawText(debt.concept, MARGIN, y, paint(12f, muted))
        y += 32
        line(context.getString(if (debt.total >= 0) R.string.to_collect else R.string.to_pay), formatMoneyExact(total), bold = true)
        line(context.getString(R.string.date), formatDate(debt.date))
        debt.dueDate?.let { line(context.getString(R.string.due_date_optional).substringBefore(" ("), formatDate(it)) }
        line(context.getString(R.string.debt_pdf_paid), formatMoneyExact(debt.paid))
        line(context.getString(R.string.debt_pdf_left), formatMoneyExact(if (debt.isActive) abs(debt.remaining) else 0), bold = true)
        y += 18
        page.canvas.drawText(context.getString(R.string.debt_history), MARGIN, y, paint(14f, Color.rgb(0xC4, 0x5F, 0x00), bold = true))
        y += 8
        if (payments.isEmpty()) {
            y += 20
            page.canvas.drawText(context.getString(R.string.debt_no_payments), MARGIN, y, paint(11f, muted))
        }
        for (payment in payments) {
            if (y + 30 > HEIGHT - MARGIN) {
                document.finishPage(page)
                number++
                page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
                y = MARGIN + 20
            }
            y += 20
            page.canvas.drawText(formatDate(payment.date), MARGIN, y, paint(11f))
            page.canvas.drawText(formatMoneyExact(payment.amount), WIDTH - MARGIN, y, paint(11f, right = true))
        }
        document.finishPage(page)
        document.writeTo(out)
        document.close()
    }
}
