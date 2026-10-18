// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.sanabria.vester.R
import com.sanabria.vester.formatMoney
import com.sanabria.vester.room.Debt

/** Persona de una deuda: lo que va tras el último " · " del concepto ("Cena · Ana" → "Ana") o el concepto entero. */
fun debtPerson(concept: String): String = concept.substringAfterLast(" · ").trim().ifEmpty { concept }

/** Persona de una deuda: la que se ha escrito o, si no, la que se deduce del concepto. */
fun debtName(debt: Debt): String = debt.person.ifEmpty { debtPerson(debt.concept) }

/**
 * Avatar con la inicial de la persona y el importe de una deuda, en verde si es a cobrar y en rojo si es a pagar.
 * Lo usan las tarjetas de deudas pendientes y saldadas.
 */
fun bindDebtLook(avatarBack: View, avatarText: TextView, amount: TextView, debt: Debt, shownAmount: Long = debt.total) {
    val context = avatarBack.context
    val color = ContextCompat.getColor(context, if (debt.total >= 0) R.color.positive else R.color.negative)
    avatarBack.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ColorUtils.setAlphaComponent(color, 0x2E))
    }
    avatarText.text = debtName(debt).firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    avatarText.setTextColor(color)
    amount.text = formatMoney(shownAmount)
    amount.setTextColor(color)
}
