// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.R
import com.sanabria.vester.databinding.ItemDayHeaderBinding
import com.sanabria.vester.databinding.TransactionLayoutBinding
import com.sanabria.vester.formatDayHeader
import com.sanabria.vester.formatMoney
import com.sanabria.vester.formatTime
import com.sanabria.vester.room.Transaction
import com.sanabria.vester.startOfDay

/** Fila de la lista de movimientos: la cabecera de un día o un movimiento. */
sealed interface TransactionRow {
    /** @property total Lo que se ha movido ese día, sin contar las transferencias entre cuentas. */
    data class Header(val dayStart: Long, val total: Long) : TransactionRow
    data class Entry(val item: TransactionItem) : TransactionRow
}

/**
 * Lista de movimientos (de una cuenta o de todas) agrupados por día, del más reciente al más antiguo.
 *
 * Si el elemento trae su cuenta, el nombre se añade junto a la hora para saber de dónde es cada movimiento.
 *
 * @param onLongClick Opciones del movimiento al mantenerlo pulsado; null para una lista de solo lectura.
 */
class AdapterTransaction(
    private val onLongClick: ((Transaction) -> Unit)? = null,
) : ListAdapter<TransactionRow, RecyclerView.ViewHolder>(DIFF) {

    /** Movimientos cargados (sin contar las cabeceras): es lo que necesita el paginado. */
    var transactionCount = 0
        private set

    /** Agrupa [items] por día y los muestra. */
    fun submit(items: List<TransactionItem>) {
        transactionCount = items.size
        submitList(groupByDay(items))
    }

    /** Movimiento mostrado en [position], o null si es una cabecera. */
    fun transactionAt(position: Int): Transaction? = (getItem(position) as? TransactionRow.Entry)?.item?.transaction

    override fun getItemViewType(position: Int) = if (getItem(position) is TransactionRow.Header) TYPE_HEADER else TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryViewHolder(TransactionLayoutBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is TransactionRow.Header -> (holder as HeaderViewHolder).bind(row)
            is TransactionRow.Entry -> (holder as EntryViewHolder).bind(row.item, onLongClick)
        }
    }

    class HeaderViewHolder(private val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: TransactionRow.Header) = with(binding) {
            dayLabel.text = formatDayHeader(root.context, header.dayStart)
            dayTotal.text = when {
                header.total > 0 -> "+" + formatMoney(header.total)
                header.total < 0 -> formatMoney(header.total)
                else -> ""
            }
        }
    }

    class EntryViewHolder(private val binding: TransactionLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionItem, onLongClick: ((Transaction) -> Unit)?) = with(binding) {
            val transaction = item.transaction
            bindRow(
                value = transaction.value,
                concept = transaction.concept,
                details = transactionDetails(item),
                isTransfer = transaction.isTransfer,
            )
            if (onLongClick == null) {
                root.setOnLongClickListener(null)
            } else {
                root.setOnLongClickListener { onLongClick(transaction); true }
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1

        /** Inserta una cabecera antes del primer movimiento de cada día. [items] llega ya ordenado por fecha. */
        fun groupByDay(items: List<TransactionItem>): List<TransactionRow> {
            val rows = ArrayList<TransactionRow>(items.size + 8)
            var index = 0
            while (index < items.size) {
                val day = startOfDay(items[index].transaction.date.time)
                val end = (index until items.size).firstOrNull { startOfDay(items[it].transaction.date.time) != day } ?: items.size
                val group = items.subList(index, end)
                rows += TransactionRow.Header(day, group.filter { !it.transaction.isTransfer }.sumOf { it.transaction.value })
                group.forEach { rows += TransactionRow.Entry(it) }
                index = end
            }
            return rows
        }

        private val DIFF = object : DiffUtil.ItemCallback<TransactionRow>() {
            override fun areItemsTheSame(oldItem: TransactionRow, newItem: TransactionRow) = when {
                oldItem is TransactionRow.Header && newItem is TransactionRow.Header -> oldItem.dayStart == newItem.dayStart
                oldItem is TransactionRow.Entry && newItem is TransactionRow.Entry ->
                    oldItem.item.transaction.transactionId == newItem.item.transaction.transactionId
                else -> false
            }

            override fun areContentsTheSame(oldItem: TransactionRow, newItem: TransactionRow) = oldItem == newItem
        }
    }
}
