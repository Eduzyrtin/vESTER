// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.R
import com.sanabria.vester.accountCardColor
import com.sanabria.vester.databinding.AccountLayoutBinding
import com.sanabria.vester.formatDateLongTime
import com.sanabria.vester.formatMoney
import com.sanabria.vester.primaryTextOn
import com.sanabria.vester.room.Account
import com.sanabria.vester.secondaryTextOn

/**
 * Tarjetas de cuentas de la pantalla principal.
 *
 * No usa ListAdapter porque, mientras se arrastra una tarjeta, el orden lo lleva el propio adapter
 * ([moveItem]) y solo se guarda en la base de datos al soltarla.
 */
class AdapterAccount(
    private val onClick: (Account, View) -> Unit,
    private val onMenu: (Account, View) -> Unit,
) : RecyclerView.Adapter<AdapterAccount.AccountViewHolder>() {

    private val items = mutableListOf<AccountCard>()

    /** Ids de las cuentas en el orden en que se ven ahora mismo. */
    val accountOrder: List<Int> get() = items.map { it.account.accountId }

    fun getAccount(position: Int): Account = items[position].account

    fun submit(cards: List<AccountCard>) {
        val old = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = cards.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].account.accountId == cards[n].account.accountId
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == cards[n]
        })
        items.clear()
        items.addAll(cards)
        diff.dispatchUpdatesTo(this)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        items.add(toPosition, items.removeAt(fromPosition))
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        AccountViewHolder(AccountLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) = holder.bind(items[position], onClick, onMenu)

    class AccountViewHolder(val binding: AccountLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        /** Cuenta que mostraba esta tarjeta: el saldo solo rueda si cambia el de la misma cuenta. */
        private var boundAccountId: Int? = null

        fun bind(card: AccountCard, onClick: (Account, View) -> Unit, onMenu: (Account, View) -> Unit) = with(binding) {
            val account = card.account
            val color = root.context.accountCardColor(account.color)
            val main = primaryTextOn(color)
            val secondary = secondaryTextOn(color)
            cardView.setCardBackgroundColor(color)
            // Degradado suave: del color elegido, aclarado en una esquina, al color elegido en la otra
            cardContent.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.22f), color),
            )
            divider.setBackgroundColor(main)
            accountName.text = if (account.icon.isEmpty()) account.name else "${account.icon}  ${account.name}"
            accountName.setTextColor(main)
            value.setText(formatMoney(account.total, account.currency), animate = boundAccountId == account.accountId)
            boundAccountId = account.accountId
            value.setTextColor(main)
            lastLabel.setTextColor(secondary)
            lastTransaction.setTextColor(secondary)
            val last = card.lastTransaction
            lastTransaction.text = if (last == null) {
                root.context.getString(R.string.no_last_transaction)
            } else {
                root.context.getString(
                    R.string.last_transaction_value,
                    formatMoney(last.value, account.currency),
                    formatDateLongTime(last.date),
                )
            }
            accountMenu.imageTintList = ColorStateList.valueOf(secondary)
            accountMenu.setOnClickListener { onMenu(account, it) }
            root.setOnClickListener { onClick(account, cardView) }
        }
    }
}
