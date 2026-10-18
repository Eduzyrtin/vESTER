// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.sanabria.vester.R
import com.sanabria.vester.formatMoneyInput
import com.sanabria.vester.parseMoney
import java.text.DecimalFormatSymbols

/**
 * Teclado numérico propio para los importes: botones grandes en una hoja inferior, sin el teclado del sistema.
 * Es una opción del menú principal (excluyente con las ruedas de [MoneyWheel]); cuando está activa, al tocar un campo de
 * importe se abre este teclado.
 */
object MoneyKeypad {
    private const val PREFS = "myPrefs"
    private const val KEY = "keypadInput"
    private val ALLOWED = Regex("""\d{0,13}([.,]\d{0,2})?""")

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, enabled) }

    /** Abre el teclado con el importe actual de [field] y, al aceptar, escribe el introducido. */
    fun show(field: EditText) {
        val context = field.context
        val separator = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
        var text = parseMoney(field.text)?.takeIf { it > 0 }?.let { formatMoneyInput(it) }.orEmpty()
        val density = context.resources.displayMetrics.density

        val display = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_App_Display)
            setTextColor(MaterialColors.getColor(this, android.R.attr.textColorPrimary))
            gravity = Gravity.END
            setPadding(0, (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }
        fun refresh() {
            display.text = text.ifEmpty { "0" }
        }

        fun key(label: String, description: String? = null, onClick: () -> Unit) = MaterialButton(
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            this.text = label
            textSize = 24f
            contentDescription = description ?: label
            setOnClickListener { onClick() }
            layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0
                height = (64 * density).toInt()
                setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            }
        }

        fun press(candidate: String) {
            if (ALLOWED.matches(candidate)) text = candidate
            refresh()
        }

        val grid = GridLayout(context).apply { columnCount = 3 }
        (1..9).forEach { digit -> grid.addView(key(digit.toString()) { press(if (text == "0") digit.toString() else text + digit) }) }
        grid.addView(key(separator) { if (!text.contains(separator)) press(text.ifEmpty { "0" } + separator) })
        grid.addView(key("0") { press(if (text == "0") text else text + "0") })
        grid.addView(key("⌫", context.getString(R.string.remove)) { text = text.dropLast(1); refresh() })

        val dialog = BottomSheetDialog(context)
        val ok = MaterialButton(context).apply {
            setText(R.string.ok)
            setOnClickListener {
                field.setText(formatMoneyInput(parseMoney(text) ?: 0L))
                dialog.dismiss()
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(display)
            addView(grid)
            addView(ok, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * density).toInt()).apply {
                topMargin = (12 * density).toInt()
            })
        }
        refresh()
        dialog.setContentView(content)
        dialog.window?.navigationBarColor =
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0)
        dialog.show()
        // Para evitar que el teclado del sistema se abra por debajo
        field.clearFocus()
        field.visibility.let { if (it != View.VISIBLE) field.visibility = View.VISIBLE }
    }
}
