// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.databinding.ActivityCoinCounterBinding
import com.sanabria.vester.databinding.ItemCoinBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import java.text.NumberFormat
import java.util.Date

/** Monedas y billetes de cada moneda, de mayor a menor, en céntimos. */
object Denominations {
    private val EURO = listOf(50000L, 20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1)
    private val DOLLAR = listOf(10000L, 5000, 2000, 1000, 500, 200, 100, 25, 10, 5, 1)
    private val POUND = listOf(5000L, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1)

    /** El primer valor que es moneda (los anteriores son billetes). */
    fun firstCoin(currency: String): Long = when (currency) {
        "$" -> 25
        "£" -> 200
        else -> 200
    }

    fun forCurrency(currency: String): List<Long> = when (currency) {
        "$" -> DOLLAR
        "£" -> POUND
        else -> EURO
    }

    /** Total en céntimos de [counts] unidades de cada valor de [values]. */
    fun total(values: List<Long>, counts: IntArray): Long = values.indices.sumOf { values[it] * counts[it] }
}

/**
 * Contador de efectivo: se cuenta cuántas monedas y billetes hay de cada valor y la app suma el total.
 * Ese total se puede apuntar como un ingreso en la cuenta elegida o usar para cuadrar su saldo
 * (se apunta un movimiento de ajuste por la diferencia, así el historial sigue cuadrando).
 */
class CoinCounter : BaseActivity() {

    private lateinit var binding: ActivityCoinCounterBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var accounts = emptyList<Account>()
    private var currency = "€"
    private var values = Denominations.forCurrency(currency)
    private var counts = IntArray(values.size)
    private val adapter = CoinAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoinCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbarCoins, getString(R.string.cash_counter))
        setNavigationBarColor(themeColor(R.attr.barColor))

        savedInstanceState?.let { state ->
            currency = state.getString(STATE_CURRENCY, currency)
            values = Denominations.forCurrency(currency)
            counts = state.getIntArray(STATE_COUNTS)?.takeIf { it.size == values.size } ?: IntArray(values.size)
        }
        binding.recyclerCoins.adapter = adapter
        binding.recyclerCoins.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.debt_columns))

        dataViewModel.accounts.observe(this) { accounts ->
            val first = this.accounts.isEmpty()
            this.accounts = accounts
            if (accounts.isEmpty()) {
                Toast.makeText(this, R.string.accounts_first, Toast.LENGTH_LONG).show()
                finish()
                return@observe
            }
            if (first) {
                binding.coinsAccount.setAccounts(accounts)
                binding.coinsAccount.onItemSelected { onAccountSelected() }
                onAccountSelected()
            }
            updateTotal(animate = false)
        }

        binding.coinsIncome.setOnClickListener { addAsIncome() }
        binding.coinsAdjust.setOnClickListener { adjustBalance() }
        updateTotal(animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENCY, currency)
        outState.putIntArray(STATE_COUNTS, counts)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_RESET, Menu.NONE, R.string.reset)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != MENU_RESET) return super.onOptionsItemSelected(item)
        counts = IntArray(values.size)
        adapter.notifyItemRangeChanged(0, values.size)
        updateTotal(animate = true)
        return true
    }

    private val selectedAccount: Account? get() = accounts.getOrNull(binding.coinsAccount.selectedItemPosition)

    /** Al cambiar a una cuenta con otra moneda, se cambian los valores (y se empieza de cero). */
    @SuppressLint("NotifyDataSetChanged") // al cambiar de moneda cambia la lista entera
    private fun onAccountSelected() {
        val account = selectedAccount ?: return
        if (account.currency != currency && Denominations.forCurrency(account.currency) != values) {
            values = Denominations.forCurrency(account.currency)
            counts = IntArray(values.size)
            adapter.notifyDataSetChanged()
        }
        currency = account.currency
        adapter.notifyItemRangeChanged(0, values.size)
        updateTotal(animate = false)
    }

    private fun total() = Denominations.total(values, counts)

    private fun updateTotal(animate: Boolean) {
        binding.coinsTotal.setText(formatMoney(total(), currency), animate)
        binding.coinsBalance.text = selectedAccount?.let {
            getString(R.string.current_balance, formatMoney(it.total, it.currency))
        }.orEmpty()
    }

    private fun addAsIncome() {
        val account = selectedAccount ?: return
        val total = total()
        if (total <= 0) {
            Toast.makeText(this, R.string.count_something_first, Toast.LENGTH_SHORT).show()
            return
        }
        dataViewModel.addTransaction(account.accountId, total, getString(R.string.cash_count), Date())
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Apunta la diferencia entre lo contado y el saldo de la cuenta para que coincidan. */
    private fun adjustBalance() {
        val account = selectedAccount ?: return
        val difference = total() - account.total
        if (difference == 0L) {
            Toast.makeText(this, R.string.balance_already_matches, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.adjust_balance)
            .setMessage(
                getString(
                    R.string.adjust_balance_message,
                    account.name,
                    formatMoney(account.total, account.currency),
                    formatMoney(total(), account.currency),
                    formatMoney(difference, account.currency),
                )
            )
            .setPositiveButton(R.string.yes) { _, _ ->
                dataViewModel.addTransaction(account.accountId, difference, getString(R.string.cash_count_adjustment), Date())
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private inner class CoinAdapter : RecyclerView.Adapter<CoinAdapter.CoinViewHolder>() {
        override fun getItemCount() = values.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CoinViewHolder(ItemCoinBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: CoinViewHolder, position: Int) = with(holder.binding) {
            val value = values[position]
            val isCoin = value <= Denominations.firstCoin(currency)
            coinStrip.setBackgroundColor(
                ContextCompat.getColor(this@CoinCounter, if (isCoin) R.color.tile_orange else R.color.positive)
            )
            coinValue.text = formatMoney(value, currency)
            coinCount.text = NumberFormat.getIntegerInstance().format(counts[position])
            coinSubtotal.text = formatMoney(value * counts[position], currency)
            coinPlus.setOnClickListener { change(holder.bindingAdapterPosition, +1) }
            coinMinus.setOnClickListener { change(holder.bindingAdapterPosition, -1) }
            // Mantener pulsado suma o resta de diez en diez
            coinPlus.setOnLongClickListener { change(holder.bindingAdapterPosition, +10); true }
            coinMinus.setOnLongClickListener { change(holder.bindingAdapterPosition, -10); true }
        }

        private fun change(position: Int, delta: Int) {
            if (position == RecyclerView.NO_POSITION) return
            counts[position] = (counts[position] + delta).coerceAtLeast(0)
            notifyItemChanged(position)
            updateTotal(animate = true)
        }

        inner class CoinViewHolder(val binding: ItemCoinBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object {
        const val STATE_CURRENCY = "currency"
        const val STATE_COUNTS = "counts"
        const val MENU_RESET = 1
    }
}
