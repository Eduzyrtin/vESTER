// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sanabria.vester.databinding.ActivityGroupDetailBinding
import com.sanabria.vester.databinding.ItemDayHeaderBinding
import com.sanabria.vester.databinding.ItemGroupRowBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.GroupBalances
import com.sanabria.vester.datamanager.Transfer
import com.sanabria.vester.datamanager.combine
import com.sanabria.vester.room.ExpenseGroup
import com.sanabria.vester.room.GroupExpense
import com.sanabria.vester.room.GroupMember
import com.sanabria.vester.tools.attachSwipeActions
import com.sanabria.vester.tools.smoothAnimations

/** Lo que se ve en el detalle de un grupo, de arriba abajo. */
sealed interface GroupDetailRow {
    data class Header(val title: Int, val key: String) : GroupDetailRow
    data class Balance(val member: GroupMember, val balance: Long) : GroupDetailRow
    data class Suggestion(val transfer: Transfer, val fromName: String, val toName: String) : GroupDetailRow
    data class Expense(val expense: GroupExpense, val payerName: String, val people: Int) : GroupDetailRow
    data class Message(val text: Int) : GroupDetailRow
}

/**
 * Detalle de un grupo de gastos compartidos: cuánto lleva cada uno, los pagos que dejan las cuentas a cero y la lista
 * de gastos. Deslizar un gasto a la derecha lo edita y a la izquierda lo borra (con "Deshacer").
 */
class GroupDetail : BaseActivity() {

    private lateinit var binding: ActivityGroupDetailBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var group: ExpenseGroup? = null
    private var members = emptyList<GroupMember>()
    private var lastBuild: Pair<List<GroupMember>, List<GroupExpense>>? = null
    private val groupId by lazy { intent.getIntExtra(EXTRA_GROUP_ID, -1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, "")

        val adapter = DetailAdapter(
            onSettle = ::confirmSettle,
            onExpense = { startActivity(GroupExpenseForm.intent(this, groupId, it.expense.expenseId)) },
        )
        binding.recyclerGroup.adapter = adapter
        binding.recyclerGroup.layoutManager = LinearLayoutManager(this)
        binding.recyclerGroup.smoothAnimations()
        binding.recyclerGroup.attachSwipeActions(
            canSwipe = { adapter.currentList.getOrNull(it) is GroupDetailRow.Expense },
            onSwipeLeft = { (adapter.currentList.getOrNull(it) as? GroupDetailRow.Expense)?.let { row -> deleteExpense(row.expense) } },
            onSwipeRight = {
                (adapter.currentList.getOrNull(it) as? GroupDetailRow.Expense)?.let { row ->
                    startActivity(GroupExpenseForm.intent(this, groupId, row.expense.expenseId))
                }
            },
        )
        binding.fab.setOnClickListener {
            if (members.size < 2) Snackbar.make(binding.root, R.string.group_need_people, Snackbar.LENGTH_LONG).show()
            else startActivity(GroupExpenseForm.intent(this, groupId))
        }

        dataViewModel.group(groupId).observe(this) {
            group = it
            if (it == null) {
                // Se ha borrado el grupo
                finish()
                return@observe
            }
            supportActionBar?.title = it.label
            if (it.closed) binding.fab.hide() else binding.fab.show()
            invalidateOptionsMenu()
            // Al cerrarse o abrirse cambia lo que se enseña
            lastBuild?.let { (members, expenses) -> adapter.submitList(buildRows(members, expenses)) }
        }
        combine(dataViewModel.groupMembers(groupId), dataViewModel.groupExpenses(groupId)) { members, expenses -> members to expenses }
            .observe(this) { (members, expenses) ->
                this.members = members
                lastBuild = members to expenses
                adapter.submitList(buildRows(members, expenses))
            }
    }

    private fun buildRows(members: List<GroupMember>, expenses: List<GroupExpense>): List<GroupDetailRow> {
        val names = members.associate { it.memberId to it.name }
        val balances = GroupBalances.balances(expenses, members.map { it.memberId })
        val rows = mutableListOf<GroupDetailRow>()
        rows += GroupDetailRow.Header(R.string.group_balances, "balances")
        members.forEach { rows += GroupDetailRow.Balance(it, balances[it.memberId] ?: 0L) }

        val transfers = GroupBalances.settlements(balances)
        rows += GroupDetailRow.Header(R.string.group_settle_up, "settle")
        if (transfers.isEmpty()) {
            rows += GroupDetailRow.Message(R.string.group_settled)
        } else if (group?.closed == true) {
            rows += GroupDetailRow.Message(R.string.group_closed_message)
        } else {
            transfers.forEach { rows += GroupDetailRow.Suggestion(it, names[it.from].orEmpty(), names[it.to].orEmpty()) }
        }

        rows += GroupDetailRow.Header(R.string.group_expenses, "expenses")
        if (expenses.isEmpty()) rows += GroupDetailRow.Message(R.string.group_no_expenses)
        expenses.forEach { rows += GroupDetailRow.Expense(it, names[it.payerId].orEmpty(), it.participantIds.size) }
        return rows
    }

    /** Pregunta antes de apuntar el pago que salda parte de las cuentas. */
    private fun confirmSettle(row: GroupDetailRow.Suggestion) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.group_pays, row.fromName, row.toName))
            .setMessage(getString(R.string.group_settle_confirm, formatMoney(row.transfer.amount)))
            .setPositiveButton(R.string.yes) { _, _ ->
                dataViewModel.addGroupExpense(
                    groupId, row.transfer.from, row.transfer.amount, getString(R.string.group_payment), java.util.Date(),
                    listOf(row.transfer.to), settlement = true,
                )
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun deleteExpense(expense: GroupExpense) {
        dataViewModel.deleteGroupExpense(expense.expenseId)
        Snackbar.make(binding.root, R.string.group_expense_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { dataViewModel.restoreGroupExpense(expense) }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_ADD_PERSON, 0, R.string.group_add_person)
        menu.add(Menu.NONE, MENU_REMOVE_PERSON, 1, R.string.group_remove_person)
        menu.add(Menu.NONE, if (group?.closed == true) MENU_REOPEN else MENU_CLOSE, 2, if (group?.closed == true) R.string.group_reopen else R.string.group_close)
        menu.add(Menu.NONE, MENU_EDIT, 3, R.string.edit_group)
        menu.add(Menu.NONE, MENU_DELETE, 4, R.string.delete_action)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_ADD_PERSON -> {
            askName()
            true
        }
        MENU_REMOVE_PERSON -> {
            askPersonToRemove()
            true
        }
        MENU_CLOSE -> {
            confirmClose()
            true
        }
        MENU_REOPEN -> {
            dataViewModel.reopenGroup(groupId)
            true
        }
        MENU_EDIT -> {
            startActivity(GroupForm.intent(this, groupId))
            true
        }
        MENU_DELETE -> {
            group?.let { confirmDeleteGroup(it) }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Cerrar crea las deudas que tocan al usuario (lo que debe y lo que le deben) y el grupo deja de admitir gastos. */
    private fun confirmClose() {
        if (group?.closed == true) return
        AlertDialog.Builder(this)
            .setTitle(R.string.group_close)
            .setMessage(R.string.group_close_sure)
            .setPositiveButton(R.string.yes) { _, _ ->
                dataViewModel.closeGroup(groupId) { created ->
                    Snackbar.make(binding.root, getString(R.string.group_closed_done, created), Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun askName() {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = getString(R.string.person_name)
        }
        val holder = FrameLayout(this).apply {
            val margin = (24 * density).toInt()
            setPadding(margin, (8 * density).toInt(), margin, 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.group_add_person)
            .setView(holder)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) dataViewModel.addGroupMember(groupId, name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askPersonToRemove() {
        val removable = members.filter { !it.isMe }
        if (removable.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.group_remove_person)
            .setItems(removable.map { it.name }.toTypedArray()) { _, which ->
                dataViewModel.removeGroupMember(groupId, removable[which].memberId) { removed ->
                    if (!removed) Snackbar.make(binding.root, getString(R.string.group_member_in_use, removable[which].name), Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteGroup(group: ExpenseGroup) {
        AlertDialog.Builder(this)
            .setTitle(group.label)
            .setMessage(R.string.group_delete_sure)
            .setPositiveButton(R.string.yes) { _, _ -> dataViewModel.deleteGroup(group.groupId) }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private class DetailAdapter(
        private val onSettle: (GroupDetailRow.Suggestion) -> Unit,
        private val onExpense: (GroupDetailRow.Expense) -> Unit,
    ) : ListAdapter<GroupDetailRow, RecyclerView.ViewHolder>(DIFF) {

        override fun getItemViewType(position: Int) = if (getItem(position) is GroupDetailRow.Header) TYPE_HEADER else TYPE_ROW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
            } else {
                RowHolder(ItemGroupRowBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is GroupDetailRow.Header -> (holder as HeaderHolder).binding.apply {
                    dayLabel.setText(row.title)
                    dayTotal.text = ""
                }
                else -> (holder as RowHolder).bind(row, onSettle, onExpense)
            }
        }

        class HeaderHolder(val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root)

        class RowHolder(private val binding: ItemGroupRowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: GroupDetailRow, onSettle: (GroupDetailRow.Suggestion) -> Unit, onExpense: (GroupDetailRow.Expense) -> Unit) = with(binding) {
                val context = root.context
                val positive = ContextCompat.getColor(context, R.color.positive)
                val negative = ContextCompat.getColor(context, R.color.negative)
                val neutral = ContextCompat.getColor(context, R.color.tile_blue)
                rowButton.visibility = View.GONE
                rowAmount.visibility = View.VISIBLE
                rowAvatarBack.visibility = View.VISIBLE
                rowRoot.setOnClickListener(null)
                rowRoot.isClickable = false
                fun avatar(text: String, color: Int) {
                    rowAvatar.text = text.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
                    rowAvatar.setTextColor(color)
                    rowAvatarBack.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorUtils.setAlphaComponent(color, 0x2E))
                    }
                }
                when (row) {
                    is GroupDetailRow.Balance -> {
                        val color = when { row.balance > 0 -> positive; row.balance < 0 -> negative; else -> neutral }
                        avatar(row.member.name, color)
                        rowTitle.text = if (row.member.isMe) context.getString(R.string.group_member_me, row.member.name) else row.member.name
                        rowSubtitle.text = context.getString(
                            when { row.balance > 0 -> R.string.group_is_owed; row.balance < 0 -> R.string.group_owes; else -> R.string.group_settled },
                        )
                        rowAmount.text = if (row.balance == 0L) "" else formatMoney(kotlin.math.abs(row.balance))
                        rowAmount.setTextColor(color)
                    }
                    is GroupDetailRow.Suggestion -> {
                        avatar(row.fromName, neutral)
                        rowTitle.text = context.getString(R.string.group_pays, row.fromName, row.toName)
                        rowSubtitle.text = ""
                        rowAmount.text = formatMoney(row.transfer.amount)
                        rowAmount.setTextColor(MaterialColorsHelper.primary(root))
                        rowButton.visibility = View.VISIBLE
                        rowButton.setOnClickListener { onSettle(row) }
                    }
                    is GroupDetailRow.Expense -> {
                        val expense = row.expense
                        val color = if (expense.settlement) positive else neutral
                        avatar(row.payerName, color)
                        rowTitle.text = expense.concept
                        rowSubtitle.text = context.getString(
                            if (expense.settlement) R.string.group_payment_by else R.string.group_expense_by,
                            row.payerName, formatDate(expense.date), row.people,
                        )
                        rowAmount.text = formatMoney(expense.amount)
                        rowAmount.setTextColor(MaterialColorsHelper.primary(root))
                        rowRoot.setOnClickListener { onExpense(row) }
                    }
                    is GroupDetailRow.Message -> {
                        rowAvatarBack.visibility = View.GONE
                        rowAmount.visibility = View.GONE
                        rowTitle.setText(row.text)
                        rowSubtitle.text = ""
                    }
                    is GroupDetailRow.Header -> Unit
                }
                rowSubtitle.visibility = if (rowSubtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        }

        private companion object {
            const val TYPE_HEADER = 0
            const val TYPE_ROW = 1

            val DIFF = object : DiffUtil.ItemCallback<GroupDetailRow>() {
                override fun areItemsTheSame(oldItem: GroupDetailRow, newItem: GroupDetailRow) = when {
                    oldItem is GroupDetailRow.Header && newItem is GroupDetailRow.Header -> oldItem.key == newItem.key
                    oldItem is GroupDetailRow.Balance && newItem is GroupDetailRow.Balance -> oldItem.member.memberId == newItem.member.memberId
                    oldItem is GroupDetailRow.Expense && newItem is GroupDetailRow.Expense -> oldItem.expense.expenseId == newItem.expense.expenseId
                    oldItem is GroupDetailRow.Suggestion && newItem is GroupDetailRow.Suggestion -> oldItem.transfer == newItem.transfer
                    oldItem is GroupDetailRow.Message && newItem is GroupDetailRow.Message -> oldItem.text == newItem.text
                    else -> false
                }

                override fun areContentsTheSame(oldItem: GroupDetailRow, newItem: GroupDetailRow) = oldItem == newItem
            }
        }
    }

    /** Color de texto principal del tema para los importes que no son ni positivos ni negativos. */
    private object MaterialColorsHelper {
        fun primary(view: View): Int = com.google.android.material.color.MaterialColors.getColor(view, android.R.attr.textColorPrimary)
    }

    companion object {
        private const val EXTRA_GROUP_ID = "groupId"
        private const val MENU_ADD_PERSON = 1
        private const val MENU_REMOVE_PERSON = 2
        private const val MENU_EDIT = 3
        private const val MENU_DELETE = 4
        private const val MENU_CLOSE = 5
        private const val MENU_REOPEN = 6

        fun intent(context: Context, groupId: Int): Intent = Intent(context, GroupDetail::class.java).putExtra(EXTRA_GROUP_ID, groupId)
    }
}
