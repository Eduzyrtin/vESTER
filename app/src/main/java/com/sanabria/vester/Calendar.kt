// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanabria.vester.databinding.ActivityCalendarBinding
import com.sanabria.vester.datamanager.AdapterCalendar
import com.sanabria.vester.datamanager.AdapterCompletedDebts
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import java.util.Calendar as JavaCalendar

/** Calendario: al elegir un día se ven sus movimientos (con la cuenta) y, debajo, las deudas de ese día. */
class Calendar : BaseActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.calendar))

        val transactions = AdapterCalendar()
        val debts = AdapterCompletedDebts { startActivity(ModifyDebt.intent(this, it.debtId)) }
        binding.recyclerCalendar.adapter = ConcatAdapter(transactions, debts)
        binding.recyclerCalendar.layoutManager = LinearLayoutManager(this)
        binding.recyclerCalendar.smoothAnimations()
        binding.recyclerCalendar.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)

        binding.emptyView.bindEmpty(R.drawable.ic_m_calendar, R.string.no_day_title)
        val showEmpty = { binding.emptyView.showWhen(transactions.currentList.isEmpty() && debts.currentList.isEmpty()) }
        dataViewModel.dayTransactions.observe(this) { transactions.submitList(it) { showEmpty() } }
        dataViewModel.dayDebts.observe(this) { debts.submitList(it) { showEmpty() } }

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val day = JavaCalendar.getInstance().apply { set(year, month, dayOfMonth) }
            dataViewModel.selectDay(day.timeInMillis)
        }
    }
}
