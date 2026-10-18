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
import com.sanabria.vester.formatTime

/** Movimientos del día elegido en el calendario, con la hora y la cuenta en el detalle. */
class AdapterCalendar : ListAdapter<TransactionItem, AdapterCalendar.CalendarViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CalendarViewHolder(TransactionLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) = holder.bind(getItem(position))

    class CalendarViewHolder(private val binding: TransactionLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionItem) = with(binding) {
            val transaction = item.transaction
            bindRow(
                value = transaction.value,
                concept = transaction.concept,
                details = transactionDetails(item),
                isTransfer = transaction.isTransfer,
            )
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TransactionItem>() {
            override fun areItemsTheSame(oldItem: TransactionItem, newItem: TransactionItem) =
                oldItem.transaction.transactionId == newItem.transaction.transactionId

            override fun areContentsTheSame(oldItem: TransactionItem, newItem: TransactionItem) = oldItem == newItem
        }
    }
}
