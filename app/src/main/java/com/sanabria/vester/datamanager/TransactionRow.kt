// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.sanabria.vester.R
import com.sanabria.vester.databinding.TransactionLayoutBinding
import com.sanabria.vester.formatMoney
import com.sanabria.vester.formatTime

/**
 * Rellena una fila de movimiento: icono de color (↑ ingreso, ↓ pago o ⇄ transferencia), concepto, detalle e importe.
 * La usan todas las listas de movimientos para que se vean igual.
 */
fun TransactionLayoutBinding.bindRow(value: Long, concept: String, details: String, isTransfer: Boolean = false) {
    val context = root.context
    val color = ContextCompat.getColor(
        context,
        when {
            isTransfer -> R.color.tile_blue
            value >= 0 -> R.color.positive
            else -> R.color.negative
        },
    )
    iconBack.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ColorUtils.setAlphaComponent(color, 0x2E))
    }
    icon.setImageResource(
        when {
            isTransfer -> R.drawable.ic_m_swap
            value >= 0 -> R.drawable.ic_m_arrow_up
            else -> R.drawable.ic_m_arrow_down
        },
    )
    icon.imageTintList = ColorStateList.valueOf(color)
    this.concept.text = concept
    subtitle.text = details
    amount.setTextColor(if (isTransfer) ContextCompat.getColor(context, R.color.tile_blue) else color)
    amount.text = formatMoney(value)
}

/** Línea de detalle de un movimiento: hora, categoría, cuenta (si se muestra), primera línea de la nota y 📎 si lleva foto, separadas por " · ". */
fun transactionDetails(item: TransactionItem): String =
    listOfNotNull(
        formatTime(item.transaction.date), item.category?.label, item.account?.name,
        item.transaction.note.takeIf { it.isNotBlank() }?.lineSequence()?.first(),
        "📎".takeIf { item.transaction.photo.isNotEmpty() },
    ).joinToString(" · ")
