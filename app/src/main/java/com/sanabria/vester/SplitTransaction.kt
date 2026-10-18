// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.sanabria.vester.tools.DropdownField
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import com.sanabria.vester.databinding.ActivitySplitTransactionBinding
import com.sanabria.vester.databinding.ItemSplitPaymentBinding
import com.sanabria.vester.databinding.ItemSplitShareBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.SplitPayment
import com.sanabria.vester.datamanager.SplitShare
import com.sanabria.vester.room.Account

/**
 * Gasto conjunto, en sus dos variantes (que se pueden combinar):
 * - Pagado desde varias cuentas: el total se reparte entre cuentas propias y se apunta un pago en cada una.
 * - Compartido con otras personas: cada persona queda debiendo su parte (se crea una deuda "a cobrar" por persona).
 *
 * Con una sola cuenta y su importe vacío, esa cuenta paga el total.
 */
class SplitTransaction : BaseActivity() {

    private lateinit var binding: ActivitySplitTransactionBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var accounts = emptyList<Account>()
    private val payments = mutableListOf<ItemSplitPaymentBinding>()
    private val shares = mutableListOf<ItemSplitShareBinding>()
    private lateinit var selectedDate: () -> Long

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplitTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbarSplit, getString(R.string.split_expense))

        binding.splitTotal.setupMoneyInput()
        binding.splitTotal.doAfterTextChanged { updateInfo() }
        selectedDate = binding.dateValue.bindDatePicker()
        if (savedInstanceState == null) {
            intent.getLongExtra(EXTRA_AMOUNT, 0L).takeIf { it > 0 }?.let { binding.splitTotal.setText(formatMoneyInput(it)) }
            intent.getStringExtra(EXTRA_CONCEPT)?.let { binding.splitConcept.setText(it) }
        }

        binding.addPayment.setOnClickListener { addPaymentRow(null) }
        binding.addShare.setOnClickListener { addShareRow() }
        binding.splitEqually.setOnClickListener { splitEqually() }
        binding.fabSplit.setOnClickListener { save() }

        dataViewModel.accounts.observe(this) { accounts ->
            if (this.accounts.isNotEmpty()) return@observe
            if (accounts.isEmpty()) {
                Toast.makeText(this, R.string.accounts_first, Toast.LENGTH_LONG).show()
                finish()
                return@observe
            }
            this.accounts = accounts
            addPaymentRow(intent.getIntExtra(EXTRA_ACCOUNT_ID, -1))
        }
    }

    private fun addPaymentRow(accountId: Int?) {
        val row = ItemSplitPaymentBinding.inflate(layoutInflater, binding.paymentsContainer, true)
        row.rowAccount.setAccounts(accounts, accountId)
        row.rowAmount.setupMoneyInput()
        row.rowAmount.doAfterTextChanged { updateInfo() }
        row.rowRemove.setOnClickListener {
            // Siempre tiene que quedar al menos una cuenta
            if (payments.size > 1) {
                payments.remove(row)
                binding.paymentsContainer.removeView(row.root)
                updateInfo()
            }
        }
        payments += row
        updateInfo()
    }

    private fun addShareRow(): ItemSplitShareBinding {
        val row = ItemSplitShareBinding.inflate(layoutInflater, binding.sharesContainer, true)
        row.rowAmount.setupMoneyInput()
        row.rowAmount.doAfterTextChanged { updateInfo() }
        row.rowRemove.setOnClickListener {
            shares.remove(row)
            binding.sharesContainer.removeView(row.root)
            updateInfo()
        }
        shares += row
        updateInfo()
        return row
    }

    /** Reparte el total a partes iguales entre las personas y yo; el céntimo que sobre me toca a mí. */
    private fun splitEqually() {
        val total = parseMoney(binding.splitTotal.text)
        if (total == null || total <= 0) {
            binding.splitTotal.requirePositiveMoney()
            return
        }
        if (shares.isEmpty()) addShareRow()
        val each = total / (shares.size + 1)
        shares.forEach { it.rowAmount.setText(formatMoneyInput(each)) }
    }

    /** Muestra lo que falta por asignar entre las cuentas y la parte que me toca pagar a mí. */
    private fun updateInfo() {
        val total = parseMoney(binding.splitTotal.text) ?: 0L
        val assigned = payments.sumOf { parseMoney(it.rowAmount.text) ?: 0L }
        binding.paymentsInfo.text = if (payments.size > 1 || assigned > 0) {
            getString(R.string.unassigned, formatMoney(total - assigned))
        } else {
            getString(R.string.single_account_pays_all)
        }
        val othersPay = shares.sumOf { parseMoney(it.rowAmount.text) ?: 0L }
        binding.myShare.text = getString(R.string.my_share, formatMoney(total - othersPay))
    }

    private fun save() {
        val total = binding.splitTotal.requirePositiveMoney() ?: return
        val concept = binding.splitConcept.textOr(getString(R.string.transaction))

        val paid = if (payments.size == 1 && binding.paymentsContainer.childCount == 1 && payments[0].rowAmount.text.isNullOrBlank()) {
            listOf(SplitPayment(accountOf(payments[0].rowAccount), total))
        } else {
            payments.map { row ->
                val amount = row.rowAmount.requirePositiveMoney() ?: return
                SplitPayment(accountOf(row.rowAccount), amount)
            }
        }
        if (paid.sumOf { it.amount } != total) {
            Toast.makeText(this, getString(R.string.payments_mismatch, formatMoney(paid.sumOf { it.amount }), formatMoney(total)), Toast.LENGTH_LONG).show()
            return
        }

        val owed = shares.mapIndexedNotNull { index, row ->
            val amount = parseMoney(row.rowAmount.text) ?: return@mapIndexedNotNull null
            if (amount <= 0) return@mapIndexedNotNull null
            SplitShare(row.rowName.textOr(getString(R.string.person_n, index + 1)), amount)
        }
        if (owed.sumOf { it.amount } > total) {
            Toast.makeText(this, R.string.shares_too_big, Toast.LENGTH_LONG).show()
            return
        }

        // Una misma cuenta repetida se junta en un solo pago
        val merged = paid.groupBy { it.accountId }.map { (id, parts) -> SplitPayment(id, parts.sumOf { it.amount }) }
        dataViewModel.addSplitExpense(concept, withCurrentTime(selectedDate()), merged, owed)
        finish()
    }

    private fun accountOf(field: DropdownField): Int = accounts[field.selectedItemPosition].accountId

    companion object {
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_CONCEPT = "concept"
        private const val EXTRA_ACCOUNT_ID = "accountId"

        /** @param amount Total propuesto en céntimos (positivo), si lo hay. */
        fun intent(context: Context, amount: Long? = null, concept: String? = null, accountId: Int? = null): Intent =
            Intent(context, SplitTransaction::class.java).apply {
                amount?.let { putExtra(EXTRA_AMOUNT, kotlin.math.abs(it)) }
                concept?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_CONCEPT, it) }
                accountId?.let { putExtra(EXTRA_ACCOUNT_ID, it) }
            }
    }
}
