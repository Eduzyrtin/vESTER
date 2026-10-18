// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityTransactionFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Category
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Modificar un movimiento ya apuntado: concepto, importe, signo y fecha. El saldo de la cuenta se corrige solo con
 * la diferencia. Usa el mismo formulario que el movimiento nuevo, sin la cuenta, la repetición ni la deuda.
 */
class ModifyTransaction : BaseActivity() {

    private lateinit var binding: ActivityTransactionFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var selectedDate: (() -> Long)? = null
    private var categories = emptyList<Category>()
    private lateinit var extras: TransactionExtras

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        extras = TransactionExtras(this, binding, dataViewModel, savedInstanceState)
        setupToolbar(binding.toolbar, getString(R.string.modify_transaction))
        listOf(binding.accountField, binding.repeatGroup, binding.makeDebtSwitch).forEach { it.visibility = View.GONE }

        binding.amountInput.setupMoneyInput()
        binding.signToggle.setup(R.string.payment, R.string.income, R.color.negative, R.color.positive)

        val id = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        lifecycleScope.launch {
            val transaction = dataViewModel.getTransaction(id)
            if (transaction == null) {
                finish()
                return@launch
            }
            if (savedInstanceState == null) {
                binding.conceptInput.setText(transaction.concept)
                binding.amountInput.setText(formatMoneyInput(transaction.value))
                binding.signToggle.isChecked = transaction.value > 0
            }
            dataViewModel.categories.observe(this@ModifyTransaction) { list ->
                val selected = if (categories.isEmpty()) transaction.categoryId else binding.categoryField.selectedCategoryId(categories)
                categories = list
                binding.categoryField.setCategories(list, selected)
            }
            extras.fill(transaction, savedInstanceState)
            selectedDate = binding.dateInput.bindDatePicker(transaction.date.time)
            binding.saveFab.setOnClickListener { save(id) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extras.saveState(outState)
    }

    private fun save(id: Int) {
        val date = selectedDate ?: return
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val value = if (binding.signToggle.isChecked) amount else -amount
        val concept = binding.conceptInput.textOr(getString(R.string.transaction))
        binding.saveFab.hapticConfirm()
        dataViewModel.updateTransaction(
            id, value, concept, Date(date()), binding.categoryField.selectedCategoryId(categories), extras.note, extras.photo,
        )
        finish()
    }

    companion object {
        private const val EXTRA_TRANSACTION_ID = "transactionId"

        fun intent(context: Context, transactionId: Int): Intent =
            Intent(context, ModifyTransaction::class.java).putExtra(EXTRA_TRANSACTION_ID, transactionId)
    }
}
