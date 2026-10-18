// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.R
import com.sanabria.vester.datamanager.AdapterAccount

/**
 * Gestos de las tarjetas de cuentas: mantener pulsado y arrastrar para ordenar; deslizar a la derecha para editar y
 * a la izquierda para borrar (con el mismo fondo de color e icono que el resto de listas). La tarjeta siempre vuelve
 * a su sitio: borrar pide confirmación. Funciona tanto en lista como en rejilla (tablets).
 */
class MainRecyclerItemTouchHelper(
    private val itemTouchHelperAdapter: ItemTouchHelperAdapter,
) : ItemTouchHelper.Callback() {

    private var dragged = false
    private val background = GradientDrawable()

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END
        val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        itemTouchHelperAdapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        dragged = true
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        viewHolder.itemView.hapticConfirm()
        // La tarjeta vuelve a su sitio; si se borra, desaparece al actualizarse la lista
        viewHolder.bindingAdapter?.notifyItemChanged(position)
        // Sin animación de cambio, la fila se quedaría desplazada: se recoloca a mano
        viewHolder.itemView.translationX = 0f
        viewHolder.itemView.alpha = 1f
        if (direction == ItemTouchHelper.LEFT) itemTouchHelperAdapter.onItemSwipedDelete(position)
        else itemTouchHelperAdapter.onItemSwipedEdit(position)
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f && viewHolder is AdapterAccount.AccountViewHolder) {
            drawSwipeBackground(c, recyclerView, viewHolder, dX)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    /** Fondo de color con el icono de la acción, con el mismo tamaño y esquinas que la tarjeta. */
    private fun drawSwipeBackground(c: Canvas, recyclerView: RecyclerView, holder: AdapterAccount.AccountViewHolder, dX: Float) {
        val context = recyclerView.context
        val density = recyclerView.resources.displayMetrics.density
        val left = dX < 0
        val card = holder.binding.cardView
        val item = holder.itemView
        val top = item.top + card.top
        val bottom = item.top + card.bottom
        val start = item.left + card.left
        val end = item.left + card.right
        background.setColor(ContextCompat.getColor(context, if (left) R.color.negative else R.color.tile_blue))
        background.cornerRadius = card.radius
        if (left) background.setBounds(end + dX.toInt(), top, end, bottom) else background.setBounds(start, top, start + dX.toInt(), bottom)
        background.draw(c)

        val icon = ContextCompat.getDrawable(context, if (left) R.drawable.ic_m_delete else R.drawable.ic_m_edit)!!.mutate()
        icon.setTint(0xFFFFFFFF.toInt())
        val size = icon.intrinsicHeight
        val margin = (24 * density).toInt()
        val iconTop = top + (bottom - top - size) / 2
        // El icono solo se dibuja cuando ya hay sitio para él
        if (Math.abs(dX) > margin + size) {
            if (left) icon.setBounds(end - margin - size, iconTop, end - margin, iconTop + size)
            else icon.setBounds(start + margin, iconTop, start + margin + size, iconTop + size)
            icon.draw(c)
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder is AdapterAccount.AccountViewHolder) {
            viewHolder.binding.cardView.cardElevation = 12f
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is AdapterAccount.AccountViewHolder) {
            viewHolder.binding.cardView.cardElevation = recyclerView.resources.displayMetrics.density * 2
        }
        // Al soltar la tarjeta se guarda el nuevo orden una sola vez
        if (dragged) {
            dragged = false
            itemTouchHelperAdapter.onItemDropped()
        }
    }
}
