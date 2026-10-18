// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.sanabria.vester.databinding.ActivityGroupExpenseFormBinding
import com.sanabria.vester.databinding.ItemGroupPartsBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.GroupExpense
import com.sanabria.vester.room.GroupMember
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Apuntar o editar un gasto de un grupo: importe, concepto, quién lo pagó, fecha y entre quiénes se reparte a partes
 * iguales (por defecto, entre todos).
 */
class GroupExpenseForm : BaseActivity() {

    private lateinit var binding: ActivityGroupExpenseFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var members = emptyList<GroupMember>()
    private var editing: GroupExpense? = null
    /** Partes de cada miembro en el reparto por partes (una por defecto). */
    private val partsByMember = mutableMapOf<Int, Int>()
    private lateinit var selectedDate: () -> Long

    private val groupId by lazy { intent.getIntExtra(EXTRA_GROUP_ID, -1) }
    private val expenseId by lazy { intent.getIntExtra(EXTRA_EXPENSE_ID, -1).takeIf { it != -1 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupExpenseFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(if (expenseId == null) R.string.group_add_expense else R.string.group_edit_expense))

        binding.amountInput.setupMoneyInput()
        binding.amountInput.doAfterTextChanged { showShare() }
        selectedDate = binding.dateInput.bindDatePicker()
        binding.splitToggle.setup(R.string.group_split_equal, R.string.group_split_parts, R.color.tile_blue, R.color.tile_blue)
        binding.splitToggle.addOnButtonCheckedListener { _, _, _ -> showParts() }
        binding.percentSwitch.setOnCheckedChangeListener { _, percent ->
            // Al pasar a porcentajes se reparte el 100 % a partes iguales para empezar
            if (percent) {
                val ids = checkedIds()
                ids.forEachIndexed { index, id -> partsByMember[id] = 100 / ids.size + if (index < 100 % ids.size) 1 else 0 }
            } else {
                partsByMember.clear()
            }
            showParts()
        }
        binding.saveFab.setOnClickListener { save() }

        lifecycleScope.launch {
            members = dataViewModel.getGroupMembers(groupId)
            if (members.isEmpty()) {
                finish()
                return@launch
            }
            val expense = expenseId?.let { dataViewModel.getGroupExpense(it) }
            editing = expense
            binding.payerField.setItems(members.map { if (it.isMe) getString(R.string.group_member_me, it.name) else it.name })
            val payer = expense?.payerId ?: members.firstOrNull { it.isMe }?.memberId
            binding.payerField.setSelection(members.indexOfFirst { it.memberId == payer }.coerceAtLeast(0))
            val selected = expense?.participantIds?.toSet() ?: members.map { it.memberId }.toSet()
            expense?.participantParts?.forEach { (id, parts) -> partsByMember[id] = parts }
            if (expense != null && expense.participantParts.any { it.second != 1 }) binding.splitToggle.isChecked = true
            showParticipants(selected)
            if (expense != null && savedInstanceState == null) {
                binding.amountInput.setText(formatMoneyInput(expense.amount))
                binding.conceptInput.setText(expense.concept)
                selectedDate = binding.dateInput.bindDatePicker(expense.date.time)
            }
            showParts()
            showShare()
        }
    }

    /** Un chip marcable por miembro, más uno "Todos" que los marca o desmarca a la vez. */
    private fun showParticipants(selected: Set<Int>) {
        val group = binding.participantChips
        group.removeAllViews()
        val inflater = LayoutInflater.from(this)
        members.forEach { member ->
            val chip = inflater.inflate(R.layout.item_chip_choice, group, false) as Chip
            chip.text = member.name
            chip.tag = member.memberId
            chip.isChecked = member.memberId in selected
            chip.setOnCheckedChangeListener { _, _ ->
                showShare()
                showParts()
            }
            group.addView(chip)
        }
        val all = inflater.inflate(R.layout.item_chip_choice, group, false) as Chip
        all.text = getString(R.string.group_select_all)
        all.isCheckable = false
        all.setOnClickListener {
            val chips = (0 until group.childCount - 1).map { group.getChildAt(it) as Chip }
            val everyoneChecked = chips.all { it.isChecked }
            chips.forEach { it.isChecked = !everyoneChecked }
        }
        group.addView(all)
    }

    /** En el reparto por partes, un campo "partes" por cada miembro marcado; en el igual no se ve nada. */
    private fun showParts() {
        val container = binding.partsContainer
        container.removeAllViews()
        val byParts = binding.splitToggle.isChecked
        container.visibility = if (byParts) View.VISIBLE else View.GONE
        binding.percentSwitch.visibility = if (byParts) View.VISIBLE else View.GONE
        if (!byParts) return
        members.filter { it.memberId in checkedIds() }.forEach { member ->
            val row = ItemGroupPartsBinding.inflate(LayoutInflater.from(this), container, false)
            row.partsLayout.hint = getString(if (binding.percentSwitch.isChecked) R.string.group_percent_of else R.string.group_parts_of, member.name)
            row.partsInput.setText((partsByMember[member.memberId] ?: 1).toString())
            row.partsInput.doAfterTextChanged { text ->
                partsByMember[member.memberId] = text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                showShare()
            }
            container.addView(row.root)
        }
        showShare()
    }

    private fun checkedIds(): List<Int> =
        (0 until binding.participantChips.childCount - 1).map { binding.participantChips.getChildAt(it) as Chip }
            .filter { it.isChecked }.map { it.tag as Int }

    /** "Cada uno paga 12,50" mientras se escribe el importe o se cambian los participantes. */
    private fun showShare() {
        if (members.isEmpty()) return
        val amount = parseMoney(binding.amountInput.text)
        val people = checkedIds().size
        binding.shareHint.visibility = if (amount != null && amount > 0 && people > 0) View.VISIBLE else View.GONE
        if (amount != null && amount > 0 && people > 0) {
            binding.shareHint.text = if (binding.splitToggle.isChecked && binding.percentSwitch.isChecked) {
                getString(R.string.group_percent_sum, checkedIds().sumOf { partsByMember[it] ?: 1 })
            } else if (binding.splitToggle.isChecked) {
                val total = checkedIds().sumOf { partsByMember[it] ?: 1 }
                getString(R.string.group_part_is, formatMoney(divideMoney(amount, total)), total)
            } else {
                getString(R.string.group_each_pays, formatMoney(divideMoney(amount, people)), people)
            }
        }
    }

    private fun save() {
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val participants = checkedIds()
        if (participants.isEmpty()) {
            binding.shareHint.visibility = View.VISIBLE
            binding.shareHint.setText(R.string.group_need_participants)
            return
        }
        val payer = members.getOrNull(binding.payerField.selectedItemPosition)?.memberId ?: return
        val concept = binding.conceptInput.textOr(getString(R.string.group_expense))
        val date = withCurrentTime(selectedDate())
        binding.saveFab.hapticConfirm()
        val parts = if (binding.splitToggle.isChecked) participants.associateWith { partsByMember[it] ?: 1 } else null
        if (binding.splitToggle.isChecked && binding.percentSwitch.isChecked && participants.sumOf { partsByMember[it] ?: 1 } != 100) {
            binding.shareHint.visibility = View.VISIBLE
            binding.shareHint.text = getString(R.string.group_percent_must, participants.sumOf { partsByMember[it] ?: 1 })
            return
        }
        val existing = editing
        if (existing == null) {
            dataViewModel.addGroupExpense(groupId, payer, amount, concept, date, participants, parts = parts)
        } else {
            dataViewModel.updateGroupExpense(existing.expenseId, payer, amount, concept, Date(date.time), participants, parts)
        }
        finish()
    }

    companion object {
        private const val EXTRA_GROUP_ID = "groupId"
        private const val EXTRA_EXPENSE_ID = "expenseId"

        /** Abre el formulario para apuntar un gasto nuevo en el grupo, o para editar [expenseId]. */
        fun intent(context: Context, groupId: Int, expenseId: Int? = null): Intent =
            Intent(context, GroupExpenseForm::class.java).putExtra(EXTRA_GROUP_ID, groupId).apply {
                expenseId?.let { putExtra(EXTRA_EXPENSE_ID, it) }
            }
    }
}
