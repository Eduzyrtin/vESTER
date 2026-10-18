// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sanabria.vester.R
import com.sanabria.vester.databinding.ActiveDebtLayoutBinding
import com.sanabria.vester.formatDate
import com.sanabria.vester.formatMoney
import com.sanabria.vester.room.Debt

/** Acciones de las tarjetas de deudas pendientes. */
interface DebtActions {
    fun onSettle(debt: Debt)
    fun onPartial(debt: Debt)
    fun onDelete(debt: Debt)
    fun onEdit(debt: Debt)
    fun onDetails(debt: Debt)
    fun onSelectionChanged(selected: Set<Int>)
}

/**
 * Deudas pendientes. Cada tarjeta tiene una casilla para saldar varias de golpe; la selección se guarda
 * aquí por id, así que no se pierde ni se mezcla al reciclar las vistas o al cambiar la lista.
 */
class AdapterDebt(private val actions: DebtActions) : ListAdapter<Debt, AdapterDebt.DebtViewHolder>(DEBT_DIFF) {

    private val selected = linkedSetOf<Int>()

    /** Deudas marcadas que siguen en la lista. */
    val selectedDebts: List<Debt> get() = currentList.filter { it.debtId in selected }

    override fun submitList(list: List<Debt>?) {
        // Las deudas que ya no están (saldadas o borradas) dejan de estar seleccionadas
        val ids = list.orEmpty().map { it.debtId }.toSet()
        if (selected.retainAll(ids)) actions.onSelectionChanged(selected.toSet())
        super.submitList(list)
    }

    fun clearSelection() {
        selected.clear()
        notifyItemRangeChanged(0, itemCount)
        actions.onSelectionChanged(emptySet())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        DebtViewHolder(ActiveDebtLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) {
        val debt = getItem(position)
        with(holder.binding) {
            val context = root.context
            bindDebtLook(avatarBack, avatarText, this.debt, debt, shownAmount = debt.remaining)
            who.text = debtName(debt)
            val kind = context.getString(if (debt.total >= 0) R.string.to_collect else R.string.to_pay)
            // Con persona escrita, el concepto pasa al subtítulo para no perderlo
            subtitle.text = listOfNotNull(
                kind, formatDate(debt.date), debt.concept.takeIf { debt.person.isNotEmpty() && it.isNotBlank() },
            ).joinToString(" · ")

            // Fecha límite (en rojo si ya pasó) y lo ya pagado
            val lines = mutableListOf<String>()
            val overdue = debt.isOverdue(System.currentTimeMillis())
            debt.dueDate?.let { lines += context.getString(if (overdue) R.string.debt_overdue else R.string.debt_due, formatDate(it)) }
            if (debt.paid > 0) lines += context.getString(R.string.debt_paid_of, formatMoney(debt.paid), formatMoney(kotlin.math.abs(debt.total)))
            extra.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
            extra.text = lines.joinToString(" · ")
            extra.setTextColor(
                if (overdue) ContextCompat.getColor(context, R.color.negative) else MaterialColors.getColor(extra, android.R.attr.textColorSecondary)
            )
            paidProgress.visibility = if (debt.paid > 0) View.VISIBLE else View.GONE
            if (debt.paid > 0) {
                paidProgress.setProgressCompat((debt.paid * 100 / kotlin.math.abs(debt.total)).toInt().coerceIn(0, 100), false)
            }

            subtitle.setOnClickListener { actions.onDetails(debt) }
            extra.setOnClickListener { actions.onDetails(debt) }
            settleButton.setOnClickListener { actions.onSettle(debt) }
            partialButton.setOnClickListener { actions.onPartial(debt) }
            deleteButton.setOnClickListener { actions.onDelete(debt) }
            root.setOnClickListener { actions.onEdit(debt) }
            root.setOnLongClickListener { actions.onEdit(debt); true }
            selectCheck.setOnCheckedChangeListener(null)
            selectCheck.isChecked = debt.debtId in selected
            selectCheck.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(debt.debtId) else selected.remove(debt.debtId)
                actions.onSelectionChanged(selected.toSet())
            }
        }
    }

    class DebtViewHolder(val binding: ActiveDebtLayoutBinding) : RecyclerView.ViewHolder(binding.root)
}
