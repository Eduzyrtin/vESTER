// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.graphics.Color
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.sanabria.vester.room.Transaction
import com.sanabria.vester.tools.RoundedBarChartRenderer

/** Movimientos que se muestran en la gráfica pequeña de las listas. */
const val SMALL_CHART_TRANSACTIONS = 15

/** Movimientos que se muestran en la gráfica a pantalla completa. */
const val FULL_CHART_TRANSACTIONS = 30

/**
 * Estilo común de las gráficas de barras: barras redondeadas, rejilla discontinua suave y textos del tema.
 * Los ingresos van en verde y los pagos en rojo, igual que en el resto de la app.
 */
fun BarChart.applyAppStyle() {
    val secondaryText = ContextCompat.getColor(context, R.color.secondaryText)
    description.isEnabled = false
    legend.textColor = secondaryText
    setNoDataText(context.getString(R.string.no_transactions))
    setNoDataTextColor(secondaryText)
    setDrawBorders(false)
    setDrawGridBackground(false)
    setBackgroundColor(Color.TRANSPARENT)
    renderer = RoundedBarChartRenderer(this, animator, viewPortHandler, resources.displayMetrics.density * 5f)
    listOf(axisLeft, axisRight).forEach {
        it.setDrawAxisLine(false)
        it.textColor = secondaryText
        it.textSize = 11f
        it.gridColor = ContextCompat.getColor(context, R.color.divider)
        it.gridLineWidth = 0.8f
        it.enableGridDashedLine(12f, 10f, 0f)
    }
    xAxis.apply {
        setDrawAxisLine(false)
        setDrawGridLines(false)
        textColor = secondaryText
        textSize = 11f
    }
}

/**
 * Dibuja [transactions] (ordenados del más reciente al más antiguo) como barras, con el más antiguo a la izquierda.
 * Los ingresos salen en verde y los pagos en rojo.
 *
 * @param animate true para animar la entrada; conviene solo en la primera carga.
 */
fun BarChart.showTransactions(transactions: List<Transaction>, animate: Boolean) {
    applyAppStyle()
    legend.isEnabled = false
    setPinchZoom(true)
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTH_SIDED
        setDrawLabels(false)
    }
    axisLeft.setDrawLabels(false)
    axisLeft.setDrawGridLines(false)
    axisRight.isInverted = false

    if (transactions.isEmpty()) {
        clear()
        return
    }
    val entries = transactions.asReversed().mapIndexed { index, transaction ->
        BarEntry(index.toFloat(), transaction.value / 100f)
    }
    val positive = ContextCompat.getColor(context, R.color.positive)
    val negative = ContextCompat.getColor(context, R.color.negative)
    data = BarData(BarDataSet(entries, "").apply {
        setColors(entries.map { if (it.y >= 0) positive else negative })
        setDrawValues(false)
    }).apply { barWidth = 0.7f }
    // El eje siempre incluye el 0 para que las barras salgan de la línea base aunque todo sean gastos o ingresos
    val min = minOf(0f, entries.minOf { it.y })
    val max = maxOf(0f, entries.maxOf { it.y }).let { if (it == min) min + 1f else it }
    listOf(axisLeft, axisRight).forEach {
        it.axisMinimum = min
        it.axisMaximum = max
    }
    if (animate) animateY(1000) else invalidate()
}
