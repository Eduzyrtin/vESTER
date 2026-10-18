// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityDebtFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch
import java.util.Date

/** Modificar una deuda pendiente: concepto, importe, signo (a cobrar o a pagar) y fecha. Mismo formulario que al crearla. */
class ModifyDebt : BaseActivity() {

    private lateinit var binding: ActivityDebtFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var selectedDate: (() -> Long)? = null
    private var selectedDue: (() -> Long?)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebtFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.modify_debt))

        binding.amountInput.setupMoneyInput()
        binding.divideButtons.bind(binding.amountInput)
        binding.signToggle.setup(R.string.debt_paid, R.string.debt_collected, R.color.negative, R.color.positive)

        val id = intent.getIntExtra(EXTRA_DEBT_ID, -1)
        lifecycleScope.launch {
            val debt = dataViewModel.getDebt(id)
            if (debt == null) {
                finish()
                return@launch
            }
            if (savedInstanceState == null) {
                binding.conceptInput.setText(debt.concept)
                binding.amountInput.setText(formatMoneyInput(debt.total))
                binding.signToggle.isChecked = debt.total >= 0
                binding.personInput.setText(debt.person)
            }
            selectedDate = binding.dateInput.bindDatePicker(debt.date.time)
            selectedDue = binding.dueInput.bindOptionalDatePicker(debt.dueDate?.time)
            binding.saveFab.setOnClickListener { save(id) }
        }
    }

    private fun save(id: Int) {
        val date = selectedDate ?: return
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val total = if (binding.signToggle.isChecked) amount else -amount
        val concept = binding.conceptInput.textOr(getString(R.string.debt))
        binding.saveFab.hapticConfirm()
        dataViewModel.updateDebt(
            id, concept, total, Date(date()),
            person = binding.personInput.text.toString().trim(), dueDate = selectedDue?.invoke()?.let { Date(it) },
        )
        finish()
    }

    companion object {
        private const val EXTRA_DEBT_ID = "debtId"

        fun intent(context: Context, debtId: Int): Intent =
            Intent(context, ModifyDebt::class.java).putExtra(EXTRA_DEBT_ID, debtId)
    }
}
