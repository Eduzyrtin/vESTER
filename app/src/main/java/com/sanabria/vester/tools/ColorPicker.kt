// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import com.google.android.material.color.MaterialColors
import com.sanabria.vester.R
import com.sanabria.vester.primaryTextOn

/**
 * Paleta de colores en cuadrícula (círculos con una marca en el elegido), para el color de una cuenta.
 * Si el color elegido no está en la paleta (una cuenta antigua, por ejemplo) se añade el primero para poder conservarlo.
 */
class ColorPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GridLayout(context, attrs) {

    private var colors: List<Int> = emptyList()
    private val swatches = mutableListOf<ImageView>()

    /** Se llama cada vez que el usuario elige un color. */
    var onColorSelected: ((Int) -> Unit)? = null

    /** Color elegido ahora mismo. */
    var selectedColor: Int = 0
        private set

    init {
        columnCount = COLUMNS
    }

    /** Muestra [palette] y deja marcado [selected] (si no está en la paleta, se añade al principio). */
    fun setup(palette: List<Int>, selected: Int) {
        colors = if (selected in palette) palette else listOf(selected) + palette
        selectedColor = selected
        removeAllViews()
        swatches.clear()
        colors.forEach { color ->
            val cell = FrameLayout(context).apply {
                layoutParams = LayoutParams(spec(UNDEFINED, 1f), spec(UNDEFINED, 1f)).apply {
                    width = 0
                    height = dp(CELL_DP)
                }
            }
            val swatch = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(SWATCH_DP), dp(SWATCH_DP), Gravity.CENTER)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = String.format("#%06X", color and 0xFFFFFF)
                setOnClickListener { select(color, notify = true) }
            }
            cell.addView(swatch)
            addView(cell)
            swatches += swatch
        }
        refresh()
    }

    /** Marca [color] (sin avisar a [onColorSelected] salvo que [notify] sea true). */
    fun select(color: Int, notify: Boolean = false) {
        selectedColor = color
        refresh()
        if (notify) onColorSelected?.invoke(color)
    }

    private fun refresh() {
        val ring = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        colors.forEachIndexed { index, color ->
            val picked = color == selectedColor
            swatches[index].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(if (picked) dp(3) else dp(1), if (picked) ring else 0x33000000)
            }
            if (picked) {
                swatches[index].setImageResource(R.drawable.ic_m_check)
                swatches[index].setColorFilter(primaryTextOn(color))
            } else {
                swatches[index].setImageDrawable(null)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLUMNS = 6
        const val CELL_DP = 52
        const val SWATCH_DP = 40
    }
}
