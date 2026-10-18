// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.sanabria.vester.databinding.ActivityCompletedDebtsBinding
import com.sanabria.vester.datamanager.AdapterCompletedDebts
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen

/** Deudas ya saldadas, de la más reciente a la más antigua. Manteniendo pulsada una se puede borrar del historial. */
class CompletedDebts : BaseActivity() {

    private lateinit var binding: ActivityCompletedDebtsBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletedDebtsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.completed_debts))

        val adapter = AdapterCompletedDebts { debt ->
            AlertDialog.Builder(this)
                .setTitle(R.string.debt_dialog)
                .setMessage(R.string.debt_sure)
                .setPositiveButton(R.string.yes) { _, _ -> dataViewModel.deleteDebt(debt.debtId) }
                .setNegativeButton(R.string.no, null)
                .show()
        }
        binding.recyclerCompletedDebts.adapter = adapter
        binding.recyclerCompletedDebts.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.debt_columns))
        binding.recyclerCompletedDebts.smoothAnimations()
        binding.emptyView.bindEmpty(R.drawable.ic_m_checkall, R.string.no_completed_debts)
        dataViewModel.completedDebts.observe(this) {
            adapter.submitList(it)
            binding.emptyView.showWhen(it.isEmpty())
        }
    }
}
