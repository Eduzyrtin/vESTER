// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors

/**
 * Colores del sistema (Material You, Android 12+) como opción. Por defecto la app usa su paleta de siempre; si se
 * activa en el menú, cada pantalla toma los colores del fondo de pantalla del usuario para sus controles y fondos.
 */
object DynamicTheme {
    private const val PREFS = "myPrefs"
    private const val KEY = "dynamicColor"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, enabled) }

    /** Aplica los colores del sistema a [activity] si el usuario los ha activado y el móvil los tiene. */
    fun apply(activity: Activity) {
        if (isEnabled(activity)) DynamicColors.applyToActivityIfAvailable(activity)
    }
}
