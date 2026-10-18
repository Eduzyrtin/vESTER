// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import com.sanabria.vester.databinding.ActivityReportsBinding
import com.sanabria.vester.databinding.ItemReportBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.PeriodTotals
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Informes de ingresos y gastos por mes o por año, de todas las cuentas o de una.
 * Las transferencias entre cuentas no cuentan: solo mueven dinero de un sitio a otro.
 * Si las cuentas tienen monedas distintas, los importes se suman sin convertir.
 */
class Reports : BaseActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var accounts = emptyList<Account>()
    private var monthly = true
    private val adapter = ReportAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbarReports, getString(R.string.reports))

        binding.reportTabs.addTab(binding.reportTabs.newTab().setText(R.string.by_month))
        binding.reportTabs.addTab(binding.reportTabs.newTab().setText(R.string.by_year))
        binding.reportTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                monthly = tab.position == 0
                refresh()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.reportList.adapter = adapter
        binding.reportList.layoutManager = LinearLayoutManager(this)
        binding.reportList.smoothAnimations()

        dataViewModel.accounts.observe(this) { accounts ->
            if (this.accounts.isNotEmpty()) return@observe
            this.accounts = accounts
            binding.reportAccount.setItems(listOf(getString(R.string.all_accounts)) + accounts.map { it.name })
            binding.reportAccount.onItemSelected { refresh() }
        }
        dataViewModel.reportTotals.observe(this) { totals ->
            adapter.submitList(totals)
            binding.emptyReports.visibility = if (totals.isEmpty()) View.VISIBLE else View.GONE
            showChart(totals.take(CHART_PERIODS).asReversed())
        }
        refresh()
    }

    private fun refresh() {
        // La posición 0 del desplegable es "Todas las cuentas"
        val accountId = accounts.getOrNull(binding.reportAccount.selectedItemPosition - 1)?.accountId
        dataViewModel.setReport(monthly, accountId)
    }

    /** Barras agrupadas: ingresos en verde y gastos en rojo, del periodo más antiguo al más reciente. */
    private fun showChart(totals: List<PeriodTotals>) = with(binding.reportChart) {
        applyAppStyle()
        setPinchZoom(false)
        isDoubleTapToZoomEnabled = false
        axisRight.isEnabled = false
        axisLeft.axisMinimum = 0f
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            valueFormatter = IndexAxisValueFormatter(totals.map { shortLabel(it.period) })
        }
        if (totals.isEmpty()) {
            clear()
            return@with
        }
        val income = BarDataSet(totals.mapIndexed { i, t -> BarEntry(i.toFloat(), t.income / 100f) }, getString(R.string.income)).apply {
            color = ContextCompat.getColor(context, R.color.positive)
            setDrawValues(false)
        }
        val expense = BarDataSet(totals.mapIndexed { i, t -> BarEntry(i.toFloat(), -t.expense / 100f) }, getString(R.string.expenses)).apply {
            color = ContextCompat.getColor(context, R.color.negative)
            setDrawValues(false)
        }
        // Ancho de grupo: 2 barras de 0,4 + 2 huecos de 0,05 + 0,1 entre grupos = 1
        data = BarData(income, expense).apply { barWidth = 0.4f }
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = totals.size.toFloat()
        groupBars(0f, 0.1f, 0.05f)
        xAxis.setCenterAxisLabels(true)
        animateY(700)
    }

    private fun shortLabel(period: String): String =
        if (period.length == 7) monthFormat("MMM yy").format(periodParser.parse(period)!!) else period

    private fun longLabel(period: String): String =
        if (period.length == 7) monthFormat("MMMM yyyy").format(periodParser.parse(period)!!).replaceFirstChar { it.uppercase() }
        else period

    private fun monthFormat(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault())
    private val periodParser = SimpleDateFormat("yyyy-MM", Locale.ROOT)

    private inner class ReportAdapter : ListAdapter<PeriodTotals, ReportAdapter.ReportViewHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ReportViewHolder(ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ReportViewHolder, position: Int) = with(holder.binding) {
            val totals = getItem(position)
            val balance = totals.income + totals.expense
            reportPeriod.text = longLabel(totals.period)
            reportIncome.text = getString(R.string.report_income, formatMoney(totals.income))
            reportExpense.text = getString(R.string.report_expense, formatMoney(-totals.expense))
            reportBalance.text = formatMoney(balance)
            reportBalance.setTextColor(
                ContextCompat.getColor(this@Reports, if (balance >= 0) R.color.positive else R.color.negative)
            )
        }

        inner class ReportViewHolder(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object {
        /** Periodos que se ven en la gráfica (los más recientes). */
        const val CHART_PERIODS = 12

        val DIFF = object : DiffUtil.ItemCallback<PeriodTotals>() {
            override fun areItemsTheSame(oldItem: PeriodTotals, newItem: PeriodTotals) = oldItem.period == newItem.period
            override fun areContentsTheSame(oldItem: PeriodTotals, newItem: PeriodTotals) = oldItem == newItem
        }
    }
}
