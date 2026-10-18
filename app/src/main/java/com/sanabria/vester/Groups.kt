// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.databinding.ActivityGroupsBinding
import com.sanabria.vester.databinding.ItemGroupBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.GroupBalances
import com.sanabria.vester.datamanager.combine
import com.sanabria.vester.room.ExpenseGroup
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.smoothAnimations

/**
 * Un grupo con sus miembros y lo que te deben ([myBalance] positivo) o debes (negativo) en él; null si no te has
 * apuntado como miembro.
 */
data class GroupRow(val group: ExpenseGroup, val memberNames: List<String>, val myBalance: Long?)

/** Grupos de gastos compartidos: un viaje, el piso, una cena... Al tocar uno se ven sus gastos y cómo saldar cuentas. */
class Groups : BaseActivity() {

    private lateinit var binding: ActivityGroupsBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.groups))

        val adapter = GroupAdapter { startActivity(GroupDetail.intent(this, it.group.groupId)) }
        binding.recyclerGroups.adapter = adapter
        binding.recyclerGroups.layoutManager = LinearLayoutManager(this)
        binding.recyclerGroups.smoothAnimations()
        binding.emptyView.bindEmpty(R.drawable.ic_m_group, R.string.no_groups_title, R.string.no_groups_hint)
        binding.fab.setOnClickListener { startActivity(GroupForm.intent(this)) }

        combine(dataViewModel.groups, dataViewModel.allGroupMembers, dataViewModel.allGroupExpenses) { groups, members, expenses ->
            groups.map { group ->
                val groupMembers = members.filter { it.groupId == group.groupId }
                val me = groupMembers.firstOrNull { it.isMe }
                val balances = GroupBalances.balances(expenses.filter { it.groupId == group.groupId }, groupMembers.map { it.memberId })
                GroupRow(group, groupMembers.map { it.name }, me?.let { balances[it.memberId] })
            }
        }.observe(this) { rows ->
            adapter.submitList(rows)
            binding.emptyView.showWhen(rows.isEmpty())
        }
    }

    private class GroupAdapter(private val onClick: (GroupRow) -> Unit) : ListAdapter<GroupRow, GroupAdapter.GroupViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            GroupViewHolder(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) = holder.bind(getItem(position), onClick)

        class GroupViewHolder(private val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: GroupRow, onClick: (GroupRow) -> Unit) = with(binding) {
                val context = root.context
                val tint = ContextCompat.getColor(context, R.color.tile_teal)
                groupIcon.text = row.group.icon.ifEmpty { "👥" }
                groupIcon.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(tint, 0x33))
                }
                groupName.text = row.group.name
                val names = row.memberNames.joinToString(", ")
                groupMembers.text = if (row.group.closed) context.getString(R.string.group_closed_names, names) else names
                val balance = row.myBalance
                when {
                    balance == null -> groupBalance.text = ""
                    balance == 0L -> {
                        groupBalance.text = context.getString(R.string.group_settled)
                        groupBalance.setTextColor(ContextCompat.getColor(context, R.color.positive))
                    }
                    balance > 0 -> {
                        groupBalance.text = context.getString(R.string.group_you_are_owed, formatMoney(balance))
                        groupBalance.setTextColor(ContextCompat.getColor(context, R.color.positive))
                    }
                    else -> {
                        groupBalance.text = context.getString(R.string.group_you_owe, formatMoney(-balance))
                        groupBalance.setTextColor(ContextCompat.getColor(context, R.color.negative))
                    }
                }
                groupCard.setOnClickListener { onClick(row) }
            }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<GroupRow>() {
                override fun areItemsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem.group.groupId == newItem.group.groupId
                override fun areContentsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem == newItem
            }
        }
    }
}
