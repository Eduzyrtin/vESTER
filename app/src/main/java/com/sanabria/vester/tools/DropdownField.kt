// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.BundleCompat
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.sanabria.vester.R

/**
 * Campo desplegable de Material (menú bajo el campo) con la forma de uso de un `Spinner`: se rellena con [setItems],
 * se lee con [selectedItemPosition] y [selectedItem] y avisa de los cambios con [onItemSelectedListener].
 * La etiqueta del campo es el `android:hint`.
 */
class DropdownField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextInputLayout(context, attrs, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle) {

    private val field = MaterialAutoCompleteTextView(getContext())
    private var items = emptyList<String>()
    private var pendingPosition: Int? = null

    /** Se llama con la posición elegida cada vez que cambia la selección. */
    var onItemSelectedListener: ((Int) -> Unit)? = null

    var selectedItemPosition = 0
        private set

    val selectedItem: String? get() = items.getOrNull(selectedItemPosition)

    init {
        field.inputType = InputType.TYPE_NULL
        field.setOnItemClickListener { _, _, position, _ -> setSelection(position) }
        addView(field, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Rellena la lista. Se queda marcada la primera opción (o la que había antes de girar la pantalla). */
    fun setItems(newItems: List<String>) {
        items = newItems
        field.setAdapter(ArrayAdapter(context, R.layout.item_spinner_dropdown, newItems))
        selectedItemPosition = (pendingPosition ?: 0).coerceIn(0, (newItems.size - 1).coerceAtLeast(0))
        pendingPosition = null
        field.setText(selectedItem.orEmpty(), false)
    }

    /** Marca la opción [position] y avisa si ha cambiado. */
    fun setSelection(position: Int) {
        if (position !in items.indices) return
        val changed = position != selectedItemPosition
        selectedItemPosition = position
        field.setText(items[position], false)
        if (changed) onItemSelectedListener?.invoke(position)
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putInt(STATE_POSITION, selectedItemPosition)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
            val position = state.getInt(STATE_POSITION)
            if (items.isEmpty()) pendingPosition = position else setSelection(position)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        const val STATE_SUPER = "super"
        const val STATE_POSITION = "position"
    }
}
