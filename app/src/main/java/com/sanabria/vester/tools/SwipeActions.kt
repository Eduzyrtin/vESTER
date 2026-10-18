// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.R

/** Pulsación suave de confirmación (si el móvil la tiene) al completar un gesto o guardar algo. */
fun View.hapticConfirm() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/**
 * Gestos de deslizar sobre las filas de una lista: a la izquierda ([onSwipeLeft], borrar) y a la derecha ([onSwipeRight], editar).
 * Mientras se desliza se ve un fondo de color con el icono de la acción. La fila siempre vuelve a su sitio: lo que
 * deba desaparecer lo decide la lista al actualizarse.
 *
 * @param canSwipe Si la fila de esa posición admite el gesto (las cabeceras de día, no).
 * @param leftColor Color e icono ([leftIcon]) del fondo al deslizar a la izquierda; [rightColor] y [rightIcon] a la derecha.
 */
fun RecyclerView.attachSwipeActions(
    canSwipe: (position: Int) -> Boolean,
    onSwipeLeft: (position: Int) -> Unit,
    onSwipeRight: (position: Int) -> Unit,
    @ColorRes leftColor: Int = R.color.negative,
    @DrawableRes leftIcon: Int = R.drawable.ic_m_delete,
    @ColorRes rightColor: Int = R.color.tile_blue,
    @DrawableRes rightIcon: Int = R.drawable.ic_m_edit,
) {
    val deleteColor = ContextCompat.getColor(context, leftColor)
    val editColor = ContextCompat.getColor(context, rightColor)
    val deleteIcon = ContextCompat.getDrawable(context, leftIcon)!!.mutate().apply { setTint(0xFFFFFFFF.toInt()) }
    val editIcon = ContextCompat.getDrawable(context, rightIcon)!!.mutate().apply { setTint(0xFFFFFFFF.toInt()) }
    val background = GradientDrawable()
    val margin = (24 * resources.displayMetrics.density).toInt()

    val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (canSwipe(viewHolder.bindingAdapterPosition)) super.getSwipeDirs(recyclerView, viewHolder) else 0

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            viewHolder.itemView.hapticConfirm()
            // La fila vuelve a su sitio; si hay que quitarla, la quita la actualización de la lista
            adapter?.notifyItemChanged(position)
            // Sin animación de cambio, la fila se quedaría desplazada: se recoloca a mano
            viewHolder.itemView.translationX = 0f
            viewHolder.itemView.alpha = 1f
            if (direction == ItemTouchHelper.LEFT) onSwipeLeft(position) else onSwipeRight(position)
        }

        override fun onChildDraw(
            c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
            dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean,
        ) {
            val item = viewHolder.itemView
            if (dX != 0f) {
                val left = dX < 0
                background.setColor(if (left) deleteColor else editColor)
                if (left) background.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
                else background.setBounds(item.left, item.top, item.left + dX.toInt(), item.bottom)
                background.draw(c)
                val icon = if (left) deleteIcon else editIcon
                val size = icon.intrinsicHeight
                val top = item.top + (item.height - size) / 2
                if (left) icon.setBounds(item.right - margin - size, top, item.right - margin, top + size)
                else icon.setBounds(item.left + margin, top, item.left + margin + size, top + size)
                icon.draw(c)
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
    ItemTouchHelper(callback).attachToRecyclerView(this)
}
