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
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.sanabria.vester.databinding.AccountLayoutCheckboxBinding
import com.sanabria.vester.databinding.ActivityAccountCombinationBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.tools.Currencies
import kotlin.math.abs

/**
 * Combinación de saldos: gráfica de tarta y total de las cuentas marcadas (o de todas si no hay ninguna marcada).
 *
 * Las cuentas en positivo forman la tarta y las que están en negativo se dibujan como un anillo dentro del agujero,
 * con su suma en el centro. Si todas las cuentas mostradas están en negativo, son ellas las que forman la tarta.
 */
class AccountCombination : BaseActivity() {

    private lateinit var binding: ActivityAccountCombinationBinding
    private val dataViewModel: DataViewModel by viewModels()
    private lateinit var adapter: AdapterCombination
    private val selected = linkedSetOf<Int>()
    private var accounts = emptyList<Account>()
    private var totalShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountCombinationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.combination))
        savedInstanceState?.getIntArray(STATE_SELECTED)?.let { selected.addAll(it.toList()) }

        adapter = AdapterCombination(selected) { account, checked ->
            if (checked) selected.add(account.accountId) else selected.remove(account.accountId)
            updateChart()
        }
        binding.accountsList.adapter = adapter
        binding.accountsList.layoutManager = LinearLayoutManager(this)
        binding.accountsList.smoothAnimations()
        setupPieChart(binding.pieChart)
        setupPieChart(binding.pieChartNegative)
        binding.pieChartNegative.apply {
            minOffset = 0f
            setTouchEnabled(false)
            isDrawHoleEnabled = true
            holeRadius = 64f
            transparentCircleRadius = 0f
            setCenterTextColor(ContextCompat.getColor(context, R.color.negative))
            setCenterTextSize(13f)
        }

        dataViewModel.accounts.observe(this) { accounts ->
            this.accounts = accounts
            selected.retainAll(accounts.map { it.accountId }.toSet())
            adapter.submitList(accounts)
            updateChart()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_SELECTED, selected.toIntArray())
    }

    private fun setupPieChart(chart: PieChart) = with(chart) {
        isDrawHoleEnabled = true
        setHoleColor(ContextCompat.getColor(context, R.color.colorBackground))
        setTransparentCircleColor(ContextCompat.getColor(context, R.color.colorBackground))
        legend.isEnabled = false
        description.isEnabled = false
        setDrawEntryLabels(false)
        setNoDataText("")
    }

    private fun updateChart() {
        val shown = if (selected.isEmpty()) accounts else accounts.filter { it.accountId in selected }
        val sum = Currencies.total(shown)
        binding.totalValue.setText(
            getString(R.string.total_value, if (sum.currency != null) formatMoney(sum.cents, sum.currency) else formatMoney(sum.cents)),
            animate = totalShown,
        )
        totalShown = true

        val positives = shown.filter { it.total > 0 }
        val negatives = shown.filter { it.total < 0 }
        val outer = positives.ifEmpty { negatives }
        val withRing = positives.isNotEmpty() && negatives.isNotEmpty()

        binding.pieChart.apply {
            centerTextRadiusPercent = 0f
            // Con anillo interior el agujero se agranda para que quepa
            holeRadius = if (withRing) 58f else 35f
            transparentCircleRadius = if (withRing) 62f else 55f
            extraTopOffset = 25f
            extraBottomOffset = 25f
        }
        if (outer.isEmpty()) {
            binding.pieChart.clear()
        } else {
            binding.pieChart.data = PieData(slices(outer, withLabels = true))
            binding.pieChart.animateY(700, Easing.EaseInOutCubic)
        }

        if (withRing) showNegativeRing(negatives) else binding.pieChartNegative.visibility = View.GONE
    }

    /** Porciones de la tarta, siempre con el valor absoluto del saldo; el importe real va en la etiqueta. */
    private fun slices(accounts: List<Account>, withLabels: Boolean) =
        PieDataSet(accounts.map { PieEntry(abs(it.total) / 100f, it.total) }, "").apply {
            colors = accounts.map { it.color }
            sliceSpace = 2f
            setDrawValues(withLabels)
            valueTextSize = 22f
            valueTextColor = ContextCompat.getColor(this@AccountCombination, R.color.secondaryText)
            valueLinePart1OffsetPercentage = 70f
            valueLinePart1Length = 0.8f
            valueLinePart2Length = 0.2f
            valueLineWidth = 2f
            isUsingSliceColorAsValueLineColor = true
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueFormatter = object : ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String =
                    formatMoney(pieEntry?.data as? Long ?: Math.round(value * 100.0))
            }
        }

    private fun showNegativeRing(negatives: List<Account>) {
        val ring = binding.pieChartNegative
        ring.visibility = View.VISIBLE
        ring.centerText = formatMoney(negatives.sumOf { it.total })
        ring.data = PieData(slices(negatives, withLabels = false))
        ring.animateY(700, Easing.EaseInOutCubic)
        // El tamaño del agujero solo se conoce cuando la tarta exterior ya se ha medido
        binding.pieChart.post {
            val outer = binding.pieChart
            val size = (outer.radius * outer.holeRadius / 100f * 2f * 0.94f).toInt()
            if (size > 0) ring.updateLayoutParams { width = size; height = size }
        }
    }

    private companion object {
        const val STATE_SELECTED = "selected"
    }
}

/** Lista de cuentas con casilla para elegir cuáles se combinan. */
class AdapterCombination(
    private val selected: Set<Int>,
    private val onToggle: (Account, Boolean) -> Unit,
) : ListAdapter<Account, AdapterCombination.CombinationViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CombinationViewHolder(AccountLayoutCheckboxBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CombinationViewHolder, position: Int) {
        val account = getItem(position)
        with(holder.binding) {
            swatch.setCardBackgroundColor(account.color)
            accountName.text = account.name
            accountName.isSelected = true
            accountTotal.text = formatMoney(account.total, account.currency)
            selectCheck.setOnCheckedChangeListener(null)
            selectCheck.isChecked = account.accountId in selected
            selectCheck.setOnCheckedChangeListener { _, checked -> onToggle(account, checked) }
            root.setOnClickListener { selectCheck.toggle() }
        }
    }

    class CombinationViewHolder(val binding: AccountLayoutCheckboxBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Account>() {
            override fun areItemsTheSame(oldItem: Account, newItem: Account) = oldItem.accountId == newItem.accountId
            override fun areContentsTheSame(oldItem: Account, newItem: Account) = oldItem == newItem
        }
    }
}
