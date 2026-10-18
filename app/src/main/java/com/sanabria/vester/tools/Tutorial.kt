// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.sanabria.vester.R

/** Un paso del tutorial: el elemento al que apunta ([target], o null para un mensaje sin foco) y su explicación. */
data class TutorialStep(val target: View?, @StringRes val text: Int)

/**
 * Tutoriales de la app: oscurecen la pantalla, dejan iluminado cada elemento y lo explican en una tarjeta con
 * "Siguiente" (o "Entendido" en el último paso). Cada uno se muestra una sola vez, salvo que se repitan con [reset].
 */
object Tutorial {
    private const val PREFS = "tutorials"

    /** Muestra los [steps] si el tutorial [id] no se había visto. Los pasos cuyo elemento no se ve (o no existe) se saltan. */
    fun show(activity: Activity, id: String, steps: List<TutorialStep>) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(id, false)) return
        val visible = steps.filter { it.target != null && it.target.isShown && it.target.width > 0 }
        if (visible.isEmpty()) return
        val root = activity.window.decorView as ViewGroup
        val overlay = SpotlightView(activity)
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.run(visible) {
            root.removeView(overlay)
            prefs.edit { putBoolean(id, true) }
        }
    }

    /** Hace que todos los tutoriales vuelvan a mostrarse. */
    fun reset(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }

    private class SpotlightView(context: Context) : FrameLayout(context) {
        private val scrim = Paint().apply { color = 0xD9000000.toInt() }
        private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        private val density = resources.displayMetrics.density
        private var hole: RectF? = null
        private val title = TextView(context).apply { textSize = 17f }
        private val button = MaterialButton(context)
        private val card = MaterialCardView(context).apply {
            radius = 20 * density
            cardElevation = 8 * density
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (20 * density).toInt()
                    setPadding(padding, padding, padding, (12 * density).toInt())
                    addView(title)
                    addView(
                        button,
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            gravity = Gravity.END
                            topMargin = (8 * density).toInt()
                        },
                    )
                }
            )
        }

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            isClickable = true
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        fun run(steps: List<TutorialStep>, onFinished: () -> Unit) {
            var index = 0
            fun show() {
                val step = steps[index]
                title.setText(step.text)
                button.setText(if (index == steps.lastIndex) R.string.got_it else R.string.tutorial_next)
                focus(step.target)
            }
            val next = {
                index++
                if (index >= steps.size) onFinished() else show()
            }
            button.setOnClickListener { next() }
            setOnClickListener { next() }
            show()
        }

        /** Ilumina [target] y coloca la tarjeta encima o debajo, donde haya más sitio. */
        private fun focus(target: View?) {
            val rect = target?.let {
                val location = IntArray(2)
                it.getLocationInWindow(location)
                val margin = 8 * density
                RectF(
                    location[0] - margin, location[1] - margin,
                    location[0] + it.width + margin, location[1] + it.height + margin,
                )
            }
            hole = rect
            val params = card.layoutParams as LayoutParams
            val gap = (16 * density).toInt()
            params.leftMargin = gap
            params.rightMargin = gap
            post {
                val height = this@SpotlightView.height
                if (rect == null) {
                    params.gravity = Gravity.CENTER
                    params.topMargin = 0
                    params.bottomMargin = 0
                } else if (rect.centerY() > height / 2f) {
                    params.gravity = Gravity.BOTTOM
                    params.topMargin = 0
                    params.bottomMargin = (height - rect.top).toInt() + gap
                } else {
                    params.gravity = Gravity.TOP
                    params.bottomMargin = 0
                    params.topMargin = rect.bottom.toInt() + gap
                }
                card.layoutParams = params
                invalidate()
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            val saved = canvas.saveLayer(null, null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
            hole?.let { canvas.drawRoundRect(it, 16 * density, 16 * density, clear) }
            canvas.restoreToCount(saved)
            super.dispatchDraw(canvas)
        }
    }
}
