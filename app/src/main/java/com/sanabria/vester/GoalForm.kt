// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityGoalFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Goal
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Crear o editar una meta de ahorro: nombre, importe, cuenta enlazada (opcional), fecha (opcional), icono y color.
 * Al editar, el menú permite borrarla.
 */
class GoalForm : BaseActivity() {

    private lateinit var binding: ActivityGoalFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private val icons by lazy { resources.getStringArray(R.array.category_icons).toList() }
    private val palette by lazy {
        listOf(
            R.color.tile_green, R.color.tile_blue, R.color.tile_orange, R.color.tile_purple,
            R.color.tile_pink, R.color.tile_teal, R.color.tile_indigo, R.color.tile_red,
        ).map { getColor(it) }
    }
    private var color = 0
    private var icon = ""
    private var goal: Goal? = null
    private var accounts = emptyList<Account>()
    private var pendingAccountId: Int? = null
    private lateinit var selectedDate: () -> Long?

    private val goalId by lazy { intent.getIntExtra(EXTRA_GOAL_ID, -1).takeIf { it != -1 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoalFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(if (goalId == null) R.string.new_goal else R.string.edit_goal))

        binding.targetInput.setupMoneyInput()
        color = savedInstanceState?.getInt(STATE_COLOR) ?: palette.first()
        icon = savedInstanceState?.getString(STATE_ICON) ?: "🎯"
        selectedDate = binding.dateInput.bindOptionalDatePicker(null)
        showChoices()
        binding.colorPicker.onColorSelected = { color = it }
        binding.saveFab.setOnClickListener { save() }

        // Cuentas enlazables: todas las visibles, tras la opción "ahorro a mano"
        dataViewModel.accounts.observe(this) { list ->
            val selected = accounts.getOrNull(binding.accountField.selectedItemPosition - 1)?.accountId ?: pendingAccountId
            accounts = list
            binding.accountField.setItems(listOf(getString(R.string.goal_no_account)) + list.map { it.name })
            binding.accountField.setSelection(list.indexOfFirst { it.accountId == selected } + 1)
        }

        goalId?.let { id ->
            lifecycleScope.launch {
                val loaded = dataViewModel.getGoal(id)
                if (loaded == null) {
                    finish()
                    return@launch
                }
                goal = loaded
                invalidateOptionsMenu()
                if (savedInstanceState == null) {
                    binding.nameInput.setText(loaded.name)
                    binding.targetInput.setText(formatMoneyInput(loaded.target))
                    color = loaded.color
                    icon = loaded.icon
                    pendingAccountId = loaded.accountId
                    binding.accountField.setSelection(accounts.indexOfFirst { it.accountId == loaded.accountId } + 1)
                    selectedDate = binding.dateInput.bindOptionalDatePicker(loaded.targetDate?.time)
                }
                showChoices()
            }
        }
    }

    private fun showChoices() {
        binding.colorPicker.setup(palette, color)
        val shown = if (icon in icons) icons else listOf(icon) + icons
        binding.iconChips.setItems(shown, selected = shown.indexOf(icon).coerceAtLeast(0))
        binding.iconChips.setOnCheckedStateChangeListener { group, _ -> icon = shown[group.selectedIndex] }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_COLOR, color)
        outState.putString(STATE_ICON, icon)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (goal != null) {
            menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete_action)
                .setIcon(R.drawable.ic_m_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != MENU_DELETE) return super.onOptionsItemSelected(item)
        val goal = goal ?: return true
        AlertDialog.Builder(this)
            .setTitle(goal.label)
            .setMessage(R.string.delete_goal_sure)
            .setPositiveButton(R.string.yes) { _, _ ->
                dataViewModel.deleteGoal(goal.goalId)
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
        return true
    }

    private fun save() {
        val name = binding.nameInput.text.toString().trim()
        if (name.isEmpty()) {
            binding.nameInput.showError(getString(R.string.required_name))
            return
        }
        val target = binding.targetInput.requirePositiveMoney() ?: return
        val accountId = accounts.getOrNull(binding.accountField.selectedItemPosition - 1)?.accountId
        val date = selectedDate()?.let { Date(it) }
        binding.saveFab.hapticConfirm()
        val existing = goal
        if (existing == null) {
            dataViewModel.addGoal(name, icon, color, target, accountId, date)
        } else {
            dataViewModel.updateGoal(existing.goalId, name, icon, color, target, accountId, date)
        }
        finish()
    }

    companion object {
        private const val EXTRA_GOAL_ID = "goalId"
        private const val STATE_COLOR = "color"
        private const val STATE_ICON = "icon"
        private const val MENU_DELETE = 1

        /** Abre el formulario para editar la meta [goalId], o para crear una nueva si es null. */
        fun intent(context: Context, goalId: Int? = null): Intent =
            Intent(context, GoalForm::class.java).apply { goalId?.let { putExtra(EXTRA_GOAL_ID, it) } }
    }
}
