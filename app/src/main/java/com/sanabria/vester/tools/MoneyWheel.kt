// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.sanabria.vester.R
import com.sanabria.vester.formatMoneyInput
import com.sanabria.vester.parseMoney
import java.text.DecimalFormatSymbols

/**
 * Introducción de importes con ruedas de cifras (como los selectores de iOS) en lugar del teclado.
 * Es una opción del menú principal; cuando está activa, al tocar un campo de importe se abren las ruedas.
 */
object MoneyWheel {
    private const val PREFS = "myPrefs"
    private const val KEY = "wheelInput"
    private const val INTEGER_DIGITS = 6
    private const val DECIMAL_DIGITS = 2

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, enabled) }

    /** Abre las ruedas con el importe actual de [field] y, al aceptar, escribe el elegido. */
    fun show(field: EditText) {
        val context = field.context
        val total = INTEGER_DIGITS + DECIMAL_DIGITS
        val maxCents = "9".repeat(total).toLong()
        val current = (parseMoney(field.text) ?: 0L).coerceIn(0L, maxCents)
        val initial = current.toString().padStart(total, '0')

        val pickers = initial.map { digit ->
            NumberPicker(context).apply {
                minValue = 0
                maxValue = 9
                value = digit.digitToInt()
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
            pickers.forEachIndexed { index, picker ->
                if (index == INTEGER_DIGITS) {
                    addView(TextView(context).apply {
                        text = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
                        textSize = 24f
                    })
                }
                addView(picker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.value)
            .setView(row)
            .setPositiveButton(R.string.ok) { _, _ ->
                val cents = pickers.joinToString("") { it.value.toString() }.toLong()
                field.setText(formatMoneyInput(cents))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
