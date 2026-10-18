// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityCategoryFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Category
import com.sanabria.vester.tools.Notifications
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch

/**
 * Crear o editar una categoría: nombre, presupuesto mensual opcional, icono y color. Al editar, el menú permite
 * borrarla (sus movimientos se quedan sin categoría).
 */
class CategoryForm : BaseActivity() {

    private lateinit var binding: ActivityCategoryFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private val icons by lazy { resources.getStringArray(R.array.category_icons).toList() }
    private val palette by lazy {
        listOf(
            R.color.tile_orange, R.color.tile_blue, R.color.tile_green, R.color.tile_purple,
            R.color.tile_red, R.color.tile_teal, R.color.tile_pink, R.color.tile_indigo,
        ).map { getColor(it) }
    }
    private var color = 0
    private var icon = ""
    private var category: Category? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    private val categoryId by lazy { intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(if (categoryId == null) R.string.new_category else R.string.edit_category))

        binding.budgetInput.setupMoneyInput()
        color = savedInstanceState?.getInt(STATE_COLOR) ?: palette.first()
        icon = savedInstanceState?.getString(STATE_ICON) ?: icons.first()
        showChoices()
        binding.colorPicker.onColorSelected = { color = it }
        binding.saveFab.setOnClickListener { save() }

        categoryId?.let { id ->
            lifecycleScope.launch {
                val loaded = dataViewModel.getCategory(id)
                if (loaded == null) {
                    finish()
                    return@launch
                }
                category = loaded
                invalidateOptionsMenu()
                if (savedInstanceState == null) {
                    binding.nameInput.setText(loaded.name)
                    if (loaded.monthlyBudget > 0) binding.budgetInput.setText(formatMoneyInput(loaded.monthlyBudget))
                    color = loaded.color
                    icon = loaded.icon
                }
                showChoices()
            }
        }
    }

    /** Marca el icono y el color elegidos (el icono se añade a la lista si no es uno de los de siempre). */
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
        if (category != null) {
            menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete_action)
                .setIcon(R.drawable.ic_m_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != MENU_DELETE) return super.onOptionsItemSelected(item)
        val category = category ?: return true
        AlertDialog.Builder(this)
            .setTitle(category.label)
            .setMessage(R.string.delete_category_sure)
            .setPositiveButton(R.string.yes) { _, _ ->
                dataViewModel.deleteCategory(category.categoryId)
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
        val budget = if (binding.budgetInput.text.isNullOrBlank()) 0L else parseMoney(binding.budgetInput.text)
        if (budget == null) {
            binding.budgetInput.showError(getString(R.string.invalid_amount))
            return
        }
        binding.saveFab.hapticConfirm()
        val existing = category
        if (existing == null) {
            dataViewModel.addCategory(name, icon, color, budget)
        } else {
            dataViewModel.updateCategory(existing.categoryId, name, icon, color, budget)
        }
        // Con presupuesto se avisa al acercarse o pasarse, así que la primera vez se pide permiso para las notificaciones
        if (budget > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Notifications.shouldAskPermission(this)) {
            Notifications.markAsked(this)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    companion object {
        private const val EXTRA_CATEGORY_ID = "categoryId"
        private const val STATE_COLOR = "color"
        private const val STATE_ICON = "icon"
        private const val MENU_DELETE = 1

        /** Abre el formulario para editar la categoría [categoryId], o para crear una nueva si es null. */
        fun intent(context: Context, categoryId: Int? = null): Intent =
            Intent(context, CategoryForm::class.java).apply { categoryId?.let { putExtra(EXTRA_CATEGORY_ID, it) } }
    }
}
