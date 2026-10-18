// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.graphics.Canvas
import android.graphics.RectF
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * Dibuja las barras con las esquinas redondeadas ([radius] en píxeles). MPAndroidChart no lo trae de serie.
 * Solo se redefine el dibujo de las barras; el resto (valores, resaltado...) es el de siempre.
 */
class RoundedBarChartRenderer(
    chart: BarDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val radius: Float,
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val rect = RectF()

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val buffer = mBarBuffers[index]
        buffer.setPhases(mAnimator.phaseX, mAnimator.phaseY)
        buffer.setDataSet(index)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.setBarWidth(mChart.barData.barWidth)
        buffer.feed(dataSet)
        mChart.getTransformer(dataSet.axisDependency).pointValuesToPixel(buffer.buffer)

        val singleColor = dataSet.colors.size == 1
        if (singleColor) mRenderPaint.color = dataSet.color
        var j = 0
        while (j < buffer.size()) {
            val left = buffer.buffer[j]
            val top = buffer.buffer[j + 1]
            val right = buffer.buffer[j + 2]
            val bottom = buffer.buffer[j + 3]
            j += 4
            if (!mViewPortHandler.isInBoundsLeft(right)) continue
            if (!mViewPortHandler.isInBoundsRight(left)) break
            if (!singleColor) mRenderPaint.color = dataSet.getColor((j - 4) / 4)
            rect.set(left, top, right, bottom)
            // Si la barra es más baja que el radio, el radio se ajusta para no deformarla
            val r = minOf(radius, rect.width() / 2f, rect.height() / 2f)
            c.drawRoundRect(rect, r, r, mRenderPaint)
        }
    }
}
