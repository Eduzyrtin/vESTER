// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanabria.vester.databinding.ViewAccountDetailBinding
import com.sanabria.vester.datamanager.AdapterTransaction
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.onScrolledNearEnd
import com.sanabria.vester.tools.showWhen

/**
 * Detalle de una cuenta: cabecera con su color y saldo, gráfica de los últimos movimientos y la lista completa.
 * Lo usan la pantalla [AccountTransaction] (móvil) y el panel derecho de la pantalla principal (tablet).
 *
 * La cuenta y sus movimientos se observan por id ([DataViewModel.selectAccount]), así que cualquier cambio
 * (un movimiento nuevo, editado o borrado) se refleja al momento sin recargar nada a mano.
 *
 * @param onDeleted Se llama cuando la cuenta mostrada se borra.
 * @param onColor Se llama con el color de la cuenta (para pintar la barra de estado, por ejemplo).
 */
class AccountDetail(
    private val activity: AppCompatActivity,
    private val binding: ViewAccountDetailBinding,
    private val dataViewModel: DataViewModel,
    private val onDeleted: () -> Unit,
    private val onColor: (Int) -> Unit = {},
) {
    private var account: Account? = null
    private var chartShown = false
    private var headerShown = false
    private var selectedId = -1

    init {
        val adapter = AdapterTransaction { activity.showTransactionOptions(it, dataViewModel) }
        binding.transactionsList.adapter = adapter
        binding.transactionsList.layoutManager = LinearLayoutManager(activity)
        binding.transactionsList.smoothAnimations()
        activity.setupTransactionSwipe(binding.transactionsList, adapter, dataViewModel)
        binding.transactionsList.layoutAnimation = AnimationUtils.loadLayoutAnimation(activity, R.anim.layout_animation_fall_down)
        binding.transactionsList.onScrolledNearEnd(loadedCount = { adapter.transactionCount }) {
            dataViewModel.loadMoreAccountTransactions(it)
        }
        binding.emptyView.bindEmpty(R.drawable.ic_m_list, R.string.no_transactions, R.string.no_transactions_hint)

        dataViewModel.account.observe(activity) { account ->
            if (account == null) {
                // La cuenta se ha borrado (si antes no había ninguna elegida, es solo que aún no hay datos)
                if (selectedId != -1) {
                    selectedId = -1
                    onDeleted()
                }
                return@observe
            }
            this.account = account
            showHeader(account)
        }
        dataViewModel.accountItems.observe(activity) { transactions ->
            adapter.submit(transactions)
            binding.emptyView.showWhen(transactions.isEmpty())
            showChart()
        }

        binding.barChart.setOnClickListener {
            account?.let { activity.startActivity(FullBarChart.intent(activity, it.accountId)) }
        }
        binding.addFab.setOnClickListener {
            account?.let { activity.startActivity(MakeTransaction.intent(activity, accountId = it.accountId)) }
        }
    }

    /** Muestra la cuenta [accountId]. Al cambiar de cuenta, el saldo y la gráfica se dibujan de nuevo sin animar el cambio. */
    fun select(accountId: Int) {
        if (selectedId != accountId) {
            headerShown = false
            chartShown = false
        }
        selectedId = accountId
        dataViewModel.selectAccount(accountId)
    }

    private fun showHeader(account: Account) {
        val color = activity.accountCardColor(account.color)
        val main = primaryTextOn(color)
        with(binding) {
            headerCard.setCardBackgroundColor(color)
            accountName.text = if (account.icon.isEmpty()) account.name else "${account.icon}  ${account.name}"
            balance.setText(formatMoney(account.total), animate = headerShown)
            currency.text = account.currency
            listOf(accountName, currency).forEach { it.setTextColor(main) }
            balance.setTextColor(main)
            balanceLabel.setTextColor(secondaryTextOn(color))
        }
        onColor(color)
        headerShown = true
    }

    /** La gráfica se dibuja cuando están los movimientos. */
    private fun showChart() {
        val transactions = dataViewModel.accountTransactions.value ?: return
        binding.barChart.showTransactions(transactions.take(SMALL_CHART_TRANSACTIONS), animate = !chartShown)
        chartShown = true
    }
}
