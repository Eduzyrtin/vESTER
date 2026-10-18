// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sanabria.vester.room.Account
import java.math.BigDecimal
import java.math.RoundingMode

/** Un total de dinero: [cents] en la moneda [currency], o sin moneda (null) si mezcla monedas sin cambio conocido. */
data class Total(val cents: Long, val currency: String?)

/**
 * Cambio de moneda manual. Las cuentas pueden estar en monedas distintas; para sumarlas el usuario elige una moneda
 * principal y escribe cuánto vale una unidad de cada otra moneda en ella (la app no consulta internet). Si falta algún
 * cambio, los totales se siguen sumando sin símbolo, como antes.
 */
object Currencies {
    private const val PREFS = "myPrefs"
    private const val KEY_MAIN = "mainCurrency"
    private const val KEY_RATES = "exchangeRates"

    private lateinit var prefs: SharedPreferences
    private var rates: Map<String, BigDecimal> = emptyMap()

    /** Moneda principal elegida, o null si todavía no se ha elegido. */
    var mainCurrency: String? = null
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mainCurrency = prefs.getString(KEY_MAIN, null)
        rates = prefs.getString(KEY_RATES, "").orEmpty().split(';').mapNotNull { entry ->
            val (currency, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            value.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { currency to it }
        }.toMap()
    }

    fun setMainCurrency(currency: String?) {
        mainCurrency = currency
        prefs.edit { if (currency == null) remove(KEY_MAIN) else putString(KEY_MAIN, currency) }
    }

    /** Cuánto vale una unidad de [currency] en la moneda principal (null si no se ha escrito). */
    fun rate(currency: String): BigDecimal? = rates[currency]

    /** Guarda el cambio de [currency]; un valor nulo o no positivo lo borra. */
    fun setRate(currency: String, rate: BigDecimal?) {
        rates = if (rate == null || rate.signum() <= 0) rates - currency else rates + (currency to rate)
        prefs.edit { putString(KEY_RATES, rates.entries.joinToString(";") { "${it.key}=${it.value.toPlainString()}" }) }
    }

    /** [cents] de [from] en céntimos de la moneda principal, o null si no hay cambio para esa moneda. */
    fun toMain(cents: Long, from: String): Long? {
        if (from == mainCurrency) return cents
        val rate = rates[from] ?: return null
        return BigDecimal.valueOf(cents).multiply(rate).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * Suma los saldos de [accounts]: con una sola moneda, tal cual; con varias, convertidos a la moneda principal si hay
     * cambio para todas, y si no, sin moneda.
     */
    fun total(accounts: List<Account>): Total {
        val currencies = accounts.map { it.currency }.distinct()
        if (currencies.size <= 1) return Total(accounts.sumOf { it.total }, currencies.singleOrNull())
        val main = mainCurrency ?: return Total(accounts.sumOf { it.total }, null)
        val converted = accounts.map { toMain(it.total, it.currency) }
        return if (converted.any { it == null }) Total(accounts.sumOf { it.total }, null) else Total(converted.sumOf { it!! }, main)
    }
}
