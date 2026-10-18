// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.R
import com.sanabria.vester.databinding.TransactionLayoutBinding
import com.sanabria.vester.formatDate
import com.sanabria.vester.room.Repeat

/**
 * Movimientos programados, con la próxima fecha, cada cuánto se repiten y la cuenta.
 * Al tocar uno se edita ([onClick]); borrar y editar también se hacen deslizando (ver `ScheduledTransactions`).
 */
class AdapterScheduled(
    private val onClick: (ScheduledItem) -> Unit,
) : ListAdapter<ScheduledItem, AdapterScheduled.ScheduledViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ScheduledViewHolder(TransactionLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ScheduledViewHolder, position: Int) = holder.bind(getItem(position), onClick)

    class ScheduledViewHolder(private val binding: TransactionLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduledItem, onClick: (ScheduledItem) -> Unit) = with(binding) {
            val scheduled = item.scheduled
            val context = root.context
            val repeat = if (scheduled.repeat == Repeat.NONE) {
                context.getString(R.string.once)
            } else {
                context.resources.getStringArray(R.array.repeat_options)[scheduled.repeat.ordinal]
            }
            val parts = listOfNotNull(formatDate(scheduled.nextDate), repeat, item.account?.name)
            bindRow(scheduled.value, scheduled.concept, parts.joinToString(" · "))
            root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScheduledItem>() {
            override fun areItemsTheSame(oldItem: ScheduledItem, newItem: ScheduledItem) =
                oldItem.scheduled.id == newItem.scheduled.id

            override fun areContentsTheSame(oldItem: ScheduledItem, newItem: ScheduledItem) = oldItem == newItem
        }
    }
}
