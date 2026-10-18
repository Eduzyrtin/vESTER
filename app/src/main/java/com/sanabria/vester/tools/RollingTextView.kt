// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip
import com.sanabria.vester.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * Texto de una línea cuyos dígitos ruedan como un cuentakilómetros (el contador de iOS) cuando cambia el valor.
 * Pensado para saldos y totales: al subir, los dígitos giran hacia arriba y, al bajar, hacia abajo.
 *
 * Acepta android:textSize, android:textColor, android:gravity y android:text en el XML, como un TextView.
 * Los caracteres se alinean por la derecha para que las unidades coincidan aunque cambie el número de cifras.
 */
class RollingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var gravity = Gravity.START or Gravity.CENTER_VERTICAL
    private var current = ""
    private var previous = ""
    private var increasing = true
    private var progress = 1f
    private var animator: ValueAnimator? = null

    /** Texto mostrado. Asignarlo anima el cambio; usa [setText] con animate = false para cambiarlo de golpe. */
    var text: CharSequence
        get() = current
        set(value) = setText(value, animate = true)

    init {
        // Cifras de ancho fijo: al cambiar el saldo las columnas no bailan
        paint.fontFeatureSettings = "tnum"
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        context.withStyledAttributes(attrs, R.styleable.RollingTextView) {
            paint.textSize = getDimension(
                R.styleable.RollingTextView_android_textSize,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics),
            )
            paint.color = getColor(R.styleable.RollingTextView_android_textColor, 0xFF212121.toInt())
            gravity = getInt(R.styleable.RollingTextView_android_gravity, gravity)
            current = getString(R.styleable.RollingTextView_android_text).orEmpty()
        }
        contentDescription = current
    }

    fun setTextColor(@ColorInt color: Int) {
        if (paint.color == color) return
        paint.color = color
        invalidate()
    }

    /**
     * Cambia el texto. Si [animate] es true y había un texto anterior, los caracteres que cambian ruedan.
     * El sentido del giro lo decide si el número (las cifras del texto) sube o baja.
     */
    fun setText(value: CharSequence, animate: Boolean) {
        val new = value.toString()
        if (new == current) return
        // La vista dibuja el texto ella misma: TalkBack lo lee por la descripción
        contentDescription = new
        animator?.cancel()
        val old = current
        current = new
        if (!animate || old.isEmpty() || !isLaidOut) {
            progress = 1f
            requestLayout()
            invalidate()
            return
        }
        previous = old
        increasing = numericValue(new) >= numericValue(old)
        progress = 0f
        requestLayout()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        progress = 1f
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textWidth = max(paint.measureText(current), if (progress < 1f) paint.measureText(previous) else 0f)
        val fm = paint.fontMetrics
        val desiredWidth = ceil(textWidth).toInt() + paddingLeft + paddingRight
        val desiredHeight = ceil(fm.bottom - fm.top).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(max(desiredWidth, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(max(desiredHeight, suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    @SuppressLint("RtlHardcoded") // Gravity.RIGHT se compara con la gravedad ya convertida a absoluta
    override fun onDraw(canvas: Canvas) {
        val fm = paint.fontMetrics
        // Como un TextView con includeFontPadding: así la línea base coincide con los textos de al lado
        val lineHeight = fm.bottom - fm.top
        val contentHeight = height - paddingTop - paddingBottom
        val top = paddingTop + when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> 0f
            Gravity.BOTTOM -> contentHeight - lineHeight
            else -> (contentHeight - lineHeight) / 2f
        }
        val baseline = top - fm.top
        val textWidth = max(paint.measureText(current), if (progress < 1f) paint.measureText(previous) else 0f)
        val contentWidth = width - paddingLeft - paddingRight
        val right = paddingLeft + when (Gravity.getAbsoluteGravity(gravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.RIGHT -> contentWidth.toFloat()
            Gravity.CENTER_HORIZONTAL -> (contentWidth + textWidth) / 2f
            else -> textWidth
        }

        canvas.withClip(0f, top, width.toFloat(), top + lineHeight) {
            var x = right
            val count = max(current.length, if (progress < 1f) previous.length else 0)
            for (i in 1..count) {
                val newChar = current.getOrNull(current.length - i)
                val oldChar = if (progress < 1f) previous.getOrNull(previous.length - i) else newChar
                val charWidth = paint.measureText((newChar ?: oldChar ?: ' ').toString())
                x -= charWidth
                drawChar(this, x, baseline, lineHeight, oldChar, newChar)
            }
        }
    }

    /** Dibuja una posición: fija si no cambia, rodando entre cifras o, si no son cifras, deslizándose. */
    private fun drawChar(canvas: Canvas, x: Float, baseline: Float, lineHeight: Float, old: Char?, new: Char?) {
        if (progress >= 1f || old == new) {
            new?.let { canvas.drawText(it.toString(), x, baseline, paint) }
            return
        }
        val direction = if (increasing) 1 else -1
        if (old != null && new != null && old.isDigit() && new.isDigit()) {
            // Como un rodillo: pasa por todas las cifras intermedias
            val from = old.digitToInt()
            val to = new.digitToInt()
            val steps = if (increasing) (to - from + 10) % 10 else (from - to + 10) % 10
            val offset = progress * steps
            for (k in 0..steps) {
                val digit = (from + direction * k + 10) % 10
                // Al subir, las cifras entran por abajo y suben; al bajar, entran por arriba y bajan
                val y = baseline + (k - offset) * lineHeight * direction
                canvas.drawText(digit.toString(), x, y, paint)
            }
        } else {
            old?.let { canvas.drawText(it.toString(), x, baseline - direction * progress * lineHeight, paint) }
            new?.let { canvas.drawText(it.toString(), x, baseline + direction * (1 - progress) * lineHeight, paint) }
        }
    }

    /** Valor aproximado del texto (solo sus cifras y el signo), para saber si sube o baja. */
    private fun numericValue(text: String): Double {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return 0.0
        val value = digits.take(18).toDouble()
        return if (text.trimStart().startsWith("-")) -value else value
    }

    private companion object {
        const val ANIMATION_MS = 650L
    }
}
