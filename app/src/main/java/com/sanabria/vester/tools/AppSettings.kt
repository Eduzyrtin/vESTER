// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** Cómo se elige el tema claro u oscuro. */
enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

/**
 * Ajustes de la app que se eligen en la pantalla de ajustes (el teclado de importes y los colores del sistema
 * tienen sus propios objetos, [MoneyWheel], [MoneyKeypad] y [DynamicTheme]). Se leen a menudo (el importe de cada fila
 * pasa por el modo discreto), así que se guardan en memoria después de [init].
 */
object AppSettings {
    private const val PREFS = "myPrefs"
    private const val KEY_THEME = "themeMode"
    private const val KEY_DISCREET = "discreetMode"
    private const val KEY_LOCK = "appLock"
    private const val KEY_MONTH_START = "monthStartDay"

    /** Último día del mes en el que se puede empezar el mes de presupuestos (así existe en todos los meses). */
    const val MAX_MONTH_START_DAY = 28

    private lateinit var prefs: SharedPreferences

    var themeMode = ThemeMode.SYSTEM
        private set
    var discreetMode = false
        private set
    var lockEnabled = false
        private set
    var monthStartDay = 1
        private set

    /** Con el modo discreto activo, los importes se ocultan hasta que el usuario los muestra con el ojo. */
    var revealed = false

    /** true si ahora mismo los importes deben verse como puntos. */
    val amountsHidden: Boolean get() = discreetMode && !revealed

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        themeMode = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.SYSTEM }
        discreetMode = prefs.getBoolean(KEY_DISCREET, false)
        lockEnabled = prefs.getBoolean(KEY_LOCK, false)
        monthStartDay = prefs.getInt(KEY_MONTH_START, 1).coerceIn(1, MAX_MONTH_START_DAY)
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putInt(KEY_THEME, mode.ordinal) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun setDiscreetMode(enabled: Boolean) {
        discreetMode = enabled
        revealed = false
        prefs.edit { putBoolean(KEY_DISCREET, enabled) }
    }

    fun setLockEnabled(enabled: Boolean) {
        lockEnabled = enabled
        prefs.edit { putBoolean(KEY_LOCK, enabled) }
    }

    fun setMonthStartDay(day: Int) {
        monthStartDay = day.coerceIn(1, MAX_MONTH_START_DAY)
        prefs.edit { putInt(KEY_MONTH_START, monthStartDay) }
    }
}
