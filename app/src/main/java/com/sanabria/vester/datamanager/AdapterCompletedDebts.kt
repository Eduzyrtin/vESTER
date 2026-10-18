// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.R
import com.sanabria.vester.databinding.InactiveDebtLayoutBinding
import com.sanabria.vester.formatDate
import com.sanabria.vester.room.Debt

/**
 * Tarjetas de deudas de solo lectura: las saldadas (con la fecha en que se saldaron) y, en el calendario,
 * también las pendientes.
 *
 * @param onLongClick Acción al mantener pulsada una tarjeta; null si no hay ninguna.
 */
class AdapterCompletedDebts(
    private val onLongClick: ((Debt) -> Unit)? = null,
) : ListAdapter<Debt, AdapterCompletedDebts.DebtViewHolder>(DEBT_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        DebtViewHolder(InactiveDebtLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) = holder.bind(getItem(position), onLongClick)

    class DebtViewHolder(private val binding: InactiveDebtLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(debt: Debt, onLongClick: ((Debt) -> Unit)?) = with(binding) {
            val context = root.context
            bindDebtLook(avatarBack, avatarText, this.debt, debt)
            who.text = debt.concept
            subtitle.text = if (debt.isActive) {
                context.getString(R.string.pending)
            } else {
                "${context.getString(R.string.done_on)} ${formatDate(debt.completed)}"
            }
            if (onLongClick == null) {
                root.setOnLongClickListener(null)
            } else {
                root.setOnLongClickListener { onLongClick(debt); true }
            }
        }
    }
}

/** Comparador común a los adapters de deudas. */
val DEBT_DIFF = object : DiffUtil.ItemCallback<Debt>() {
    override fun areItemsTheSame(oldItem: Debt, newItem: Debt) = oldItem.debtId == newItem.debtId
    override fun areContentsTheSame(oldItem: Debt, newItem: Debt) = oldItem == newItem
}
