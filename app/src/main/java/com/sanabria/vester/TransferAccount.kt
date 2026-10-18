// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.sanabria.vester.databinding.ActivityTransferAccountBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.tools.hapticConfirm

/** Transferencia entre dos cuentas propias: sale de una y entra en la otra en la misma operación. */
class TransferAccount : BaseActivity() {

    private lateinit var binding: ActivityTransferAccountBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var accounts: List<Account>? = null
    private var targets = emptyList<Account>()
    private lateinit var selectedDate: () -> Long

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.transfer))

        binding.amountInput.setupMoneyInput()
        selectedDate = binding.dateInput.bindDatePicker()

        dataViewModel.accounts.observe(this) { accounts ->
            if (this.accounts != null) return@observe
            if (accounts.size < 2) {
                Toast.makeText(this, R.string.accounts_first, Toast.LENGTH_LONG).show()
                finish()
                return@observe
            }
            this.accounts = accounts
            binding.fromField.setAccounts(accounts)
            binding.fromField.onItemSelected { position -> updateTargets(accounts, accounts[position]) }
            updateTargets(accounts, accounts[binding.fromField.selectedItemPosition])
        }
        binding.saveFab.setOnClickListener { save() }
    }

    /** Las cuentas de destino son todas menos la de origen; se conserva la elegida si sigue siendo posible. */
    private fun updateTargets(accounts: List<Account>, from: Account) {
        val previous = targets.getOrNull(binding.toField.selectedItemPosition)?.accountId
        targets = accounts.filter { it.accountId != from.accountId }
        binding.toField.setAccounts(targets, previous)
    }

    private fun save() {
        val accounts = accounts ?: return
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val from = accounts[binding.fromField.selectedItemPosition]
        val to = targets.getOrNull(binding.toField.selectedItemPosition) ?: return
        binding.saveFab.hapticConfirm()
        dataViewModel.transfer(
            from.accountId,
            to.accountId,
            amount,
            withCurrentTime(selectedDate()),
            "${getString(R.string.to)} ${to.name}",
            "${getString(R.string.from)} ${from.name}",
        )
        finish()
    }
}
