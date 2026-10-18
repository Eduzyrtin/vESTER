// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.sanabria.vester.databinding.ActivityFullBarChartBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Transaction

/** Gráfica a pantalla completa con los últimos [FULL_CHART_TRANSACTIONS] movimientos de una cuenta o de todas. */
class FullBarChart : BaseActivity() {

    private lateinit var binding: ActivityFullBarChartBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var chartShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullBarChartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColor(themeColor(android.R.attr.colorBackground))

        val show = { transactions: List<Transaction> ->
            binding.barChart.showTransactions(transactions.take(FULL_CHART_TRANSACTIONS), animate = !chartShown)
            chartShown = true
        }
        val accountId = intent.getIntExtra(EXTRA_ACCOUNT_ID, NO_ACCOUNT)
        if (accountId == NO_ACCOUNT) {
            dataViewModel.allTransactions.observe(this) { items -> show(items.map { it.transaction }) }
        } else {
            dataViewModel.selectAccount(accountId)
            dataViewModel.accountTransactions.observe(this) { show(it) }
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "accountId"
        private const val NO_ACCOUNT = -1

        /** @param accountId Cuenta a mostrar, o null para todas. */
        fun intent(context: Context, accountId: Int?): Intent =
            Intent(context, FullBarChart::class.java)
                .putExtra(EXTRA_ACCOUNT_ID, accountId ?: NO_ACCOUNT)
    }
}
