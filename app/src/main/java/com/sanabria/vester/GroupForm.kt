// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityGroupFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch

/**
 * Crear un grupo (nombre, cómo te llamas en él, las demás personas e icono) o cambiar el nombre y el icono de uno
 * que ya existe. Las personas se añaden o quitan después desde el propio grupo.
 */
class GroupForm : BaseActivity() {

    private lateinit var binding: ActivityGroupFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private val icons by lazy { resources.getStringArray(R.array.group_icons).toList() }
    private var icon = ""

    private val groupId by lazy { intent.getIntExtra(EXTRA_GROUP_ID, -1).takeIf { it != -1 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(if (groupId == null) R.string.new_group else R.string.edit_group))

        icon = savedInstanceState?.getString(STATE_ICON) ?: icons.first()
        if (savedInstanceState == null && groupId == null) binding.myNameInput.setText(getString(R.string.group_me))
        showIcons()
        binding.saveFab.setOnClickListener { save() }

        groupId?.let { id ->
            // Al editar solo se cambia el nombre y el icono
            binding.myNameLayout.visibility = View.GONE
            binding.othersLayout.visibility = View.GONE
            lifecycleScope.launch {
                val group = dataViewModel.groupOnce(id)
                if (group == null) {
                    finish()
                    return@launch
                }
                if (savedInstanceState == null) {
                    binding.nameInput.setText(group.name)
                    icon = group.icon
                }
                showIcons()
            }
        }
    }

    private fun showIcons() {
        val shown = if (icon in icons || icon.isEmpty()) icons else listOf(icon) + icons
        binding.iconChips.setItems(shown, selected = shown.indexOf(icon).coerceAtLeast(0))
        binding.iconChips.setOnCheckedStateChangeListener { group, _ -> icon = shown[group.selectedIndex] }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ICON, icon)
    }

    private fun save() {
        val name = binding.nameInput.text.toString().trim()
        if (name.isEmpty()) {
            binding.nameInput.showError(getString(R.string.required_name))
            return
        }
        binding.saveFab.hapticConfirm()
        val id = groupId
        if (id != null) {
            dataViewModel.renameGroup(id, name, icon)
            finish()
            return
        }
        val others = binding.othersInput.text.toString().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (others.isEmpty()) {
            binding.othersInput.showError(getString(R.string.group_need_people))
            return
        }
        val myName = binding.myNameInput.text.toString().trim().ifEmpty { getString(R.string.group_me) }
        dataViewModel.addGroup(name, icon, myName, others) { newId ->
            startActivity(GroupDetail.intent(this, newId))
            finish()
        }
    }

    companion object {
        private const val EXTRA_GROUP_ID = "groupId"
        private const val STATE_ICON = "icon"

        /** Abre el formulario para editar el grupo [groupId], o para crear uno nuevo si es null. */
        fun intent(context: Context, groupId: Int? = null): Intent =
            Intent(context, GroupForm::class.java).apply { groupId?.let { putExtra(EXTRA_GROUP_ID, it) } }
    }
}
