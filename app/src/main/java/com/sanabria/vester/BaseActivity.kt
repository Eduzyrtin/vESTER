// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.sanabria.vester.tools.AppLock
import com.sanabria.vester.tools.AppSettings
import com.sanabria.vester.tools.DynamicTheme
import kotlin.math.max

/**
 * Base de todas las pantallas.
 *
 * Desde Android 15 las apps se dibujan por detrás de la barra de estado y de la de navegación. Para conservar
 * el aspecto de siempre (barra de estado del color de la barra superior), el contenido de cada pantalla se
 * envuelve entre dos franjas del alto de esas barras: la de arriba usa el color primario del tema y la de abajo
 * es transparente salvo que la pantalla diga otra cosa. La franja de abajo también crece con el teclado.
 */
abstract class BaseActivity : AppCompatActivity() {

    private lateinit var statusBarView: View
    private lateinit var navigationBarView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicTheme.apply(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        // Con el bloqueo activo, las capturas de pantalla y la vista de apps recientes no enseñan los importes
        if (AppSettings.lockEnabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        AppLock.guard(this)
    }

    override fun setContentView(layoutResID: Int) {
        setContentView(layoutInflater.inflate(layoutResID, FrameLayout(this), false))
    }

    override fun setContentView(view: View?) {
        statusBarView = View(this)
        navigationBarView = View(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusBarView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
            addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(navigationBarView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            statusBarView.updateLayoutParams { height = bars.top }
            navigationBarView.updateLayoutParams { height = max(bars.bottom, ime.bottom) }
            v.updatePadding(left = bars.left, right = bars.right)
            WindowInsetsCompat.CONSUMED
        }
        super.setContentView(container)
        setStatusBarColor(themeColor(R.attr.barColor))
        setNavigationBarColor(themeColor(android.R.attr.colorBackground))
    }

    /** Pinta la franja de la barra de estado y elige iconos claros u oscuros según el color. */
    protected fun setStatusBarColor(@ColorInt color: Int) {
        statusBarView.setBackgroundColor(color)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isColorDark(color)
    }

    /** Pinta la franja de la barra de navegación (por ejemplo, del color de una barra inferior). */
    protected fun setNavigationBarColor(@ColorInt color: Int) {
        navigationBarView.setBackgroundColor(color)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isColorDark(color)
    }

    /** Usa [toolbar] como barra de acciones con el título [title]. */
    protected fun setupToolbar(toolbar: Toolbar, title: CharSequence) {
        toolbar.title = title
        setSupportActionBar(toolbar)
    }
}

/** Color de un atributo del tema actual. */
@ColorInt
fun Activity.themeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) getColor(value.resourceId) else value.data
}
