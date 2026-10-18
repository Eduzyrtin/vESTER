// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.sanabria.vester.databinding.DivideButtonsBinding
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.tools.DropdownField

/*
 * Piezas comunes de los formularios (cuentas, movimientos, transferencias y deudas).
 */

/** Ejecuta [block] con la posición elegida cada vez que cambia la selección del desplegable. */
fun DropdownField.onItemSelected(block: (position: Int) -> Unit) {
    onItemSelectedListener = block
}

/** Desplegable de cuentas. Devuelve la posición de [preselectedId] (o 0 si no está). */
fun DropdownField.setAccounts(accounts: List<Account>, preselectedId: Int? = null): Int {
    setItems(accounts.map { it.name })
    val position = accounts.indexOfFirst { it.accountId == preselectedId }.coerceAtLeast(0)
    setSelection(position)
    return position
}

/** Rellena el grupo de chips de elección única con [items] y marca el de la posición [selected]. */
fun ChipGroup.setItems(items: List<String>, selected: Int = 0) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    items.forEachIndexed { index, text ->
        val chip = inflater.inflate(R.layout.item_chip_choice, this, false) as Chip
        chip.id = View.generateViewId()
        chip.text = text
        chip.tag = index
        addView(chip)
    }
    getChildAt(selected.coerceIn(0, (items.size - 1).coerceAtLeast(0)))?.let { check(it.id) }
}

/** Posición del chip elegido. */
val ChipGroup.selectedIndex: Int
    get() = (findViewById<Chip>(checkedChipId)?.tag as? Int) ?: 0

/** Colores de la paleta que se puede elegir para una cuenta. */
fun Context.accountPalette(): List<Int> {
    val colors = resources.obtainTypedArray(R.array.account_palette)
    try {
        return List(colors.length()) { colors.getColor(it, 0) }
    } finally {
        colors.recycle()
    }
}

/**
 * Marca [message] como error del campo (bajo el campo si está en un campo Material) y lo quita en cuanto se escribe.
 */
fun EditText.showError(message: String) {
    val layout = generateSequence(parent) { it.parent }.filterIsInstance<TextInputLayout>().firstOrNull()
    if (layout != null) layout.error = message else error = message
    requestFocus()
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (layout != null) {
                layout.error = null
                layout.isErrorEnabled = false
            } else {
                error = null
            }
            removeTextChangedListener(this)
        }
    })
}

/**
 * Lee un importe mayor que cero del campo. Si no lo es, marca el error en el campo y devuelve null.
 */
fun EditText.requirePositiveMoney(): Long? {
    val cents = parseMoney(text)
    if (cents == null || cents <= 0) {
        showError(context.getString(R.string.invalid_amount))
        return null
    }
    return cents
}

/** Conecta los botones de dividir (÷2, ÷3, ÷4 y ÷N) con el campo de importe [amount]. */
fun DivideButtonsBinding.bind(amount: EditText) {
    val divide = { parts: Int ->
        parseMoney(amount.text)?.let { amount.setText(formatMoneyInput(divideMoney(it, parts))) }
    }
    divideBy2.setOnClickListener { divide(2) }
    divideBy3.setOnClickListener { divide(3) }
    divideBy4.setOnClickListener { divide(4) }
    divideByN.setOnClickListener {
        val picker = NumberPicker(root.context).apply {
            minValue = 2
            maxValue = 50
            value = 5
            wrapSelectorWheel = false
        }
        AlertDialog.Builder(root.context)
            .setTitle(R.string.divide_into)
            .setView(picker)
            .setPositiveButton(R.string.ok) { _, _ -> divide(picker.value) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

/** Texto del campo sin espacios sobrantes, o [default] si está vacío. */
fun EditText.textOr(default: String): String = text.toString().trim().ifEmpty { default }

/**
 * Desplegable de categorías con "Sin categoría" como primera opción. Marca la de [preselectedId] (si no está, la
 * primera). Se usa [selectedCategoryId] para saber cuál se ha elegido.
 */
fun DropdownField.setCategories(categories: List<Category>, preselectedId: Int? = null) {
    setItems(listOf(context.getString(R.string.no_category)) + categories.map { it.label })
    setSelection(categories.indexOfFirst { it.categoryId == preselectedId } + 1)
}

/** Id de la categoría elegida en un desplegable rellenado con [setCategories], o null si es "Sin categoría". */
fun DropdownField.selectedCategoryId(categories: List<Category>): Int? =
    categories.getOrNull(selectedItemPosition - 1)?.categoryId
