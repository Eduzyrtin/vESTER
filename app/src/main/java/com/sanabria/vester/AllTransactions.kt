// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanabria.vester.databinding.ActivityAllTransactionsBinding
import com.sanabria.vester.datamanager.AdapterTransaction
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.onScrolledNearEnd

/** Todos los movimientos de todas las cuentas, del más reciente al más antiguo, con su cuenta junto a la fecha. */
class AllTransactions : BaseActivity() {

    private lateinit var binding: ActivityAllTransactionsBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var chartShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.all_transactions))

        val adapter = AdapterTransaction { showTransactionOptions(it, dataViewModel) }
        binding.recyclerTransactions.adapter = adapter
        binding.recyclerTransactions.layoutManager = LinearLayoutManager(this)
        binding.recyclerTransactions.smoothAnimations()
        setupTransactionSwipe(binding.recyclerTransactions, adapter, dataViewModel)
        binding.recyclerTransactions.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)
        binding.recyclerTransactions.onScrolledNearEnd(loadedCount = { adapter.transactionCount }) { dataViewModel.loadMoreTransactions(it) }

        binding.emptyView.bindEmpty(R.drawable.ic_m_list, R.string.no_transactions, R.string.no_transactions_hint)
        dataViewModel.allTransactions.observe(this) { items ->
            adapter.submit(items)
            binding.emptyView.showWhen(items.isEmpty())
            val latest = items.take(SMALL_CHART_TRANSACTIONS).map { it.transaction }
            binding.barChart.showTransactions(latest, animate = !chartShown)
            chartShown = true
        }

        binding.barChart.setOnClickListener { startActivity(FullBarChart.intent(this, null)) }
    }
}
