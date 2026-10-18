// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.InputFilter
import android.text.Spanned
import android.widget.EditText
import androidx.core.graphics.ColorUtils
import com.sanabria.vester.tools.MoneyKeypad
import com.sanabria.vester.tools.MoneyWheel
import com.sanabria.vester.tools.AppSettings
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/*
 * Todos los importes de la app son Long en céntimos. Nunca se pasa por Double: 0.29 * 100 en Double
 * es 28.999999999999996, que al truncar se guardaba como 28 céntimos y descuadraba los saldos.
 */

/** Máximo de cifras enteras que se pueden escribir (cabe de sobra en un Long en céntimos). */
private const val MAX_INTEGER_DIGITS = 13
private val MONEY_INPUT = Regex("\\d{0,$MAX_INTEGER_DIGITS}([.,]\\d{0,2})?")

/**
 * Convierte lo que escribe el usuario ("12,5", "12.50", "3") en céntimos.
 * Acepta coma o punto como separador decimal. Devuelve null si el texto no es un importe válido.
 */
fun parseMoney(text: CharSequence?): Long? {
    val clean = text?.toString()?.trim()?.replace(" ", "")?.replace(',', '.') ?: return null
    if (clean.isEmpty() || clean == "." || clean == "-") return null
    return try {
        BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/** Importe con dos decimales y separadores del idioma del móvil: 1234567 → "12.345,67" en español. */
fun formatMoney(cents: Long, locale: Locale = Locale.getDefault()): String =
    if (AppSettings.amountsHidden) HIDDEN_AMOUNT else formatMoneyExact(cents, locale)

/** Lo mismo que [formatMoney] pero sin ocultar nada con el modo discreto: para lo que se exporta (informes en PDF). */
fun formatMoneyExact(cents: Long, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return format.format(BigDecimal.valueOf(cents, 2))
}

/** Lo que se ve en lugar de un importe con el modo discreto activo. */
const val HIDDEN_AMOUNT = "••••"

/** Importe con su moneda: "12,50 €". */
fun formatMoney(cents: Long, currency: String): String = "${formatMoney(cents)} $currency"

/** Importe sin signo ni separador de miles, listo para editarlo en un campo de texto: -1234567 → "12345,67". */
fun formatMoneyInput(cents: Long, locale: Locale = Locale.getDefault()): String {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return BigDecimal.valueOf(abs(cents), 2).toPlainString().replace('.', separator)
}

/** Parte de un importe dividido entre [parts], redondeando el céntimo sobrante hacia arriba. */
fun divideMoney(cents: Long, parts: Int): Long =
    BigDecimal.valueOf(cents).divide(BigDecimal(parts), 0, RoundingMode.HALF_UP).toLong()

/**
 * Filtro para campos de importe: solo deja escribir números con, como mucho, un separador decimal y dos decimales.
 * Comprueba cómo quedaría el texto completo después del cambio, no solo lo que había antes.
 */
class MoneyInputFilter : InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        val result = StringBuilder(dest).replace(dstart, dend, source.subSequence(start, end).toString())
        return if (MONEY_INPUT.matches(result)) null else dest.subSequence(dstart, dend)
    }
}

/**
 * Prepara un EditText para escribir importes. Si están activadas las ruedas ([MoneyWheel]) o el teclado propio
 * ([MoneyKeypad]), el campo no abre el teclado del sistema: al tocarlo se elige el importe con ellos.
 */
fun EditText.setupMoneyInput() {
    filters = arrayOf(MoneyInputFilter())
    val picker: ((EditText) -> Unit)? = when {
        MoneyWheel.isEnabled(context) -> MoneyWheel::show
        MoneyKeypad.isEnabled(context) -> MoneyKeypad::show
        else -> null
    }
    if (picker != null) {
        isFocusable = false
        isFocusableInTouchMode = false
        isCursorVisible = false
        setOnClickListener { picker(this) }
    }
}

/** true si el color es oscuro y necesita texto claro encima. */
fun isColorDark(color: Int): Boolean {
    val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
    return darkness >= 0.5
}

/** true si sobre [background] se lee mejor el texto blanco que el oscuro (el de más contraste, no solo por brillo). */
fun prefersLightText(background: Int): Boolean =
    ColorUtils.calculateContrast(Color.WHITE, background) >= ColorUtils.calculateContrast(0xFF212121.toInt(), background)

/** Color de texto principal legible sobre [background]. */
fun primaryTextOn(background: Int): Int = if (prefersLightText(background)) Color.WHITE else 0xFF212121.toInt()

/** Color de texto secundario legible sobre [background]. */
fun secondaryTextOn(background: Int): Int = if (prefersLightText(background)) 0xEBFFFFFF.toInt() else 0xFF3E3E3E.toInt()

/**
 * Color con el que se pinta una tarjeta de cuenta cuyo color elegido es [color].
 * De noche se oscurece conservando el tono (los pasteles parecidos siguen distinguiéndose) para que no deslumbre; el
 * texto se elige después sobre este color.
 */
fun Context.accountCardColor(color: Int): Int {
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return if (night) nightCardColor(color) else color
}

/** Versión oscura de un color de tarjeta: mismo tono, luminosidad entre 0,18 y 0,32 y algo más de intensidad si tenía color. */
fun nightCardColor(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = if (hsl[1] < 0.05f) hsl[1] else (hsl[1] * 0.8f + 0.08f).coerceAtMost(0.6f)
    hsl[2] = 0.18f + hsl[2] * 0.14f
    return ColorUtils.HSLToColor(hsl)
}
