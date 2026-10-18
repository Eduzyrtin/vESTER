// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityAccountFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch

/** Lo que se ha escrito en el formulario de una cuenta, ya validado. */
data class AccountInput(val name: String, val total: Long, val currency: String, val color: Int, val icon: String)

/**
 * Formulario de una cuenta (nombre, saldo con su signo, moneda, icono y color) con una tarjeta de vista previa que
 * cambia con lo que se elige. Lo comparten [AddAccount] y [EditAccount], que solo deciden qué hacer al guardar.
 */
abstract class AccountFormActivity : BaseActivity() {

    protected lateinit var binding: ActivityAccountFormBinding
    protected val dataViewModel: DataViewModel by viewModels()
    private var color = 0
    private var icon = ""
    private var previewName = ""
    private val icons by lazy { resources.getStringArray(R.array.account_icons).toList() }

    /** Título de la pantalla. */
    protected abstract val screenTitle: Int

    /** Se llama al pulsar "Guardar" con el formulario ya validado. */
    protected abstract fun onSave(input: AccountInput)

    protected fun setupForm(savedInstanceState: Bundle?, initialColor: Int) {
        binding = ActivityAccountFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(screenTitle))

        binding.balanceInput.setupMoneyInput()
        binding.signToggle.setup(R.string.positive, R.string.negative, R.color.positive, R.color.negative)
        binding.currencyField.setItems(resources.getStringArray(R.array.currency).toList())

        color = savedInstanceState?.getInt(STATE_COLOR) ?: initialColor
        icon = savedInstanceState?.getString(STATE_ICON) ?: icon
        binding.colorPicker.setup(accountPalette(), color)
        binding.colorPicker.onColorSelected = {
            color = it
            showPreview()
        }
        binding.iconChips.setItems(listOf(getString(R.string.icon_none)) + icons, selected = iconChipIndex())
        binding.iconChips.setOnCheckedStateChangeListener { group, _ ->
            val index = group.selectedIndex
            icon = if (index == 0) "" else icons[index - 1]
            showPreview()
        }
        binding.nameInput.doAfterTextChanged { showPreview() }
        binding.saveFab.setOnClickListener { save() }
        showPreview()
    }

    /** Pone el formulario como una cuenta que ya existe (para editarla). */
    protected fun fill(account: Account, savedInstanceState: Bundle?) {
        previewName = account.name
        if (savedInstanceState == null) {
            binding.nameInput.setText(account.name)
            binding.balanceInput.setText(formatMoneyInput(account.total))
            binding.signToggle.isChecked = account.total < 0
            binding.currencyField.setSelection(resources.getStringArray(R.array.currency).indexOf(account.currency).coerceAtLeast(0))
            color = account.color
            icon = account.icon
        }
        binding.colorPicker.setup(accountPalette(), color)
        binding.iconChips.setItems(listOf(getString(R.string.icon_none)) + icons, selected = iconChipIndex())
        showPreview()
    }

    private fun iconChipIndex() = if (icon.isEmpty()) 0 else icons.indexOf(icon) + 1

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_COLOR, color)
        outState.putString(STATE_ICON, icon)
    }

    /** La tarjeta de muestra, con el color, el icono y el nombre escritos (o uno de ejemplo). */
    private fun showPreview() {
        val shown = accountCardColor(color)
        val textColor = primaryTextOn(shown)
        binding.previewCard.setCardBackgroundColor(shown)
        binding.previewName.text = binding.nameInput.text?.toString()?.trim().orEmpty()
            .ifEmpty { previewName.ifEmpty { getString(R.string.account_test) } }
        binding.previewName.setTextColor(textColor)
        binding.previewIcon.text = icon
        binding.previewIcon.visibility = if (icon.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun save() {
        val name = binding.nameInput.text.toString().trim()
        if (name.isEmpty()) {
            binding.nameInput.showError(getString(R.string.required_name))
            return
        }
        val amount = if (binding.balanceInput.text.isNullOrBlank()) 0L else parseMoney(binding.balanceInput.text)
        if (amount == null) {
            binding.balanceInput.showError(getString(R.string.invalid_amount))
            return
        }
        val total = if (binding.signToggle.isChecked) -amount else amount
        binding.saveFab.hapticConfirm()
        onSave(AccountInput(name, total, binding.currencyField.selectedItem.orEmpty(), color, icon))
    }

    private companion object {
        const val STATE_COLOR = "color"
        const val STATE_ICON = "icon"
    }
}

/** Formulario para crear una cuenta. El saldo inicial puede ser positivo o negativo. */
class AddAccount : AccountFormActivity() {

    override val screenTitle = R.string.add_account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupForm(savedInstanceState, initialColor = accountPalette().first())
        binding.balanceLayout.hint = getString(R.string.initial_value)
    }

    override fun onSave(input: AccountInput) {
        dataViewModel.addAccount(input.name, input.total, input.color, input.currency, input.icon)
        finish()
    }
}

/**
 * Edición de una cuenta. El saldo que se escribe aquí sustituye al actual (sirve para cuadrarlo a mano).
 * Si el color actual no está en la paleta (cuentas antiguas), aparece el primero para poder conservarlo.
 */
class EditAccount : AccountFormActivity() {

    override val screenTitle = R.string.title_activity_edit_account
    private var account: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupForm(savedInstanceState, initialColor = accountPalette().first())
        binding.balanceInput.setSelectAllOnFocus(true)

        val id = intent.getIntExtra(EXTRA_ACCOUNT_ID, -1)
        lifecycleScope.launch {
            val account = dataViewModel.getAccount(id)
            if (account == null) {
                finish()
                return@launch
            }
            this@EditAccount.account = account
            fill(account, savedInstanceState)
        }
    }

    override fun onSave(input: AccountInput) {
        val account = account ?: return
        dataViewModel.updateAccount(account.accountId, input.name, input.total, input.color, input.currency, input.icon)
        finish()
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "accountId"

        fun intent(context: Context, accountId: Int): Intent =
            Intent(context, EditAccount::class.java).putExtra(EXTRA_ACCOUNT_ID, accountId)
    }
}
