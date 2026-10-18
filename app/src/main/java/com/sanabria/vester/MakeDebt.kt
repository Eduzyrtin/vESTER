// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.sanabria.vester.databinding.ActivityDebtFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.hapticConfirm
import java.util.Date

/**
 * Crear una deuda: a cobrar (me deben) o a pagar (debo). Se puede abrir ya rellena, por ejemplo al marcar un
 * movimiento como "pagado por otra persona".
 */
class MakeDebt : BaseActivity() {

    private lateinit var binding: ActivityDebtFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private lateinit var selectedDate: () -> Long
    private lateinit var selectedDue: () -> Long?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebtFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.make_debt))

        binding.amountInput.setupMoneyInput()
        selectedDate = binding.dateInput.bindDatePicker()
        selectedDue = binding.dueInput.bindOptionalDatePicker(null)
        binding.signToggle.setup(R.string.debt_paid, R.string.debt_collected, R.color.negative, R.color.positive)
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_CONCEPT)?.let { binding.conceptInput.setText(it) }
            if (intent.hasExtra(EXTRA_AMOUNT)) {
                val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
                binding.amountInput.setText(formatMoneyInput(amount))
                binding.signToggle.isChecked = amount >= 0
            }
        }
        binding.divideButtons.bind(binding.amountInput)
        binding.saveFab.setOnClickListener { save() }
    }

    private fun save() {
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val total = if (binding.signToggle.isChecked) amount else -amount
        val concept = binding.conceptInput.textOr(getString(R.string.debt))
        binding.saveFab.hapticConfirm()
        dataViewModel.addDebt(
            concept, total, withCurrentTime(selectedDate()),
            person = binding.personInput.text.toString().trim(), dueDate = selectedDue()?.let { Date(it) },
        )
        finish()
    }

    companion object {
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_CONCEPT = "concept"

        /** @param amount Céntimos: positivo si es a cobrar y negativo si es a pagar. */
        fun intent(context: Context, amount: Long, concept: String): Intent =
            Intent(context, MakeDebt::class.java)
                .putExtra(EXTRA_AMOUNT, amount)
                .putExtra(EXTRA_CONCEPT, concept)
    }
}
