// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.sanabria.vester.R

/**
 * Selector de dos opciones con botones, para el signo de un importe: pago / ingreso, a pagar / a cobrar...
 * La opción de la izquierda es "no marcada" y la de la derecha, "marcada"; [isChecked] funciona como el de un interruptor.
 * Hay que configurarlo con [setup] (textos y colores de cada opción).
 */
class SignToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialButtonToggleGroup(context, attrs) {

    private val left = newButton(R.id.sign_unchecked)
    private val right = newButton(R.id.sign_checked)

    init {
        isSingleSelection = true
        isSelectionRequired = true
        addView(left)
        addView(right)
        check(R.id.sign_unchecked)
    }

    /** true si está marcada la opción de la derecha. */
    var isChecked: Boolean
        get() = checkedButtonId == R.id.sign_checked
        set(value) = check(if (value) R.id.sign_checked else R.id.sign_unchecked)

    /**
     * @param leftColor Color de la opción de la izquierda cuando está elegida.
     * @param rightColor Color de la de la derecha.
     * @param onChange Se llama al configurarlo y cada vez que cambia la opción elegida.
     */
    fun setup(
        @StringRes leftText: Int,
        @StringRes rightText: Int,
        @ColorRes leftColor: Int,
        @ColorRes rightColor: Int,
        onChange: (checked: Boolean) -> Unit = {},
    ) {
        left.setText(leftText)
        right.setText(rightText)
        style(left, ContextCompat.getColor(context, leftColor))
        style(right, ContextCompat.getColor(context, rightColor))
        addOnButtonCheckedListener { _, id, isChecked -> if (isChecked) onChange(id == R.id.sign_checked) }
        onChange(isChecked)
    }

    private fun newButton(buttonId: Int) = MaterialButton(
        context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
    ).apply {
        id = buttonId
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        minHeight = (52 * resources.displayMetrics.density).toInt()
        isAllCaps = false
    }

    /** Elegida: texto y borde del color de la opción y fondo suave de ese color; sin elegir: neutra. */
    private fun style(button: MaterialButton, color: Int) {
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val neutral = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
        button.backgroundTintList = ColorStateList(
            arrayOf(checkedState, intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(color, 0x2E), Color.TRANSPARENT),
        )
        button.setTextColor(ColorStateList(arrayOf(checkedState, intArrayOf()), intArrayOf(color, neutral)))
        button.strokeColor = ColorStateList(arrayOf(checkedState, intArrayOf()), intArrayOf(color, outline))
    }
}
