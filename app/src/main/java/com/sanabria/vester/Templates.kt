// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sanabria.vester.databinding.ActivityTemplatesBinding
import com.sanabria.vester.databinding.ItemGroupRowBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.combine
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.room.Template
import com.sanabria.vester.tools.attachSwipeActions
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.hapticConfirm
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.smoothAnimations

/** Plantilla con el nombre de su cuenta y de su categoría ya resueltos. */
data class TemplateRow(val template: Template, val account: Account?, val category: Category?)

/**
 * Movimientos frecuentes ("Café 1,50", "Bono bus 20"): al tocar uno se apunta ahora mismo. Deslizar a la izquierda lo
 * borra (con "Deshacer"). Se crean con el botón de abajo (el mismo formulario del movimiento) o desde el menú del
 * formulario con "Guardar como plantilla".
 */
class Templates : BaseActivity() {

    private lateinit var binding: ActivityTemplatesBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplatesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.templates))

        val adapter = TemplateAdapter(::apply)
        binding.recyclerTemplates.adapter = adapter
        binding.recyclerTemplates.layoutManager = LinearLayoutManager(this)
        binding.recyclerTemplates.smoothAnimations()
        binding.emptyView.bindEmpty(R.drawable.ic_m_bolt, R.string.no_templates_title, R.string.no_templates_hint)
        binding.fab.setOnClickListener { startActivity(MakeTransaction.templateIntent(this)) }
        binding.recyclerTemplates.attachSwipeActions(
            canSwipe = { it in 0 until adapter.itemCount },
            onSwipeLeft = { delete(adapter.currentList[it].template) },
            onSwipeRight = { apply(adapter.currentList[it]) },
            rightColor = R.color.positive,
            rightIcon = R.drawable.ic_m_check,
        )

        combine(dataViewModel.templates, dataViewModel.allAccounts, dataViewModel.categories) { templates, accounts, categories ->
            val accountById = accounts.associateBy { it.accountId }
            val categoryById = categories.associateBy { it.categoryId }
            templates.map { TemplateRow(it, accountById[it.accountId], categoryById[it.categoryId]) }
        }.observe(this) { rows ->
            adapter.submitList(rows)
            binding.emptyView.showWhen(rows.isEmpty())
        }
    }

    private fun apply(row: TemplateRow) {
        binding.root.hapticConfirm()
        dataViewModel.applyTemplate(row.template.templateId) { applied ->
            val message = if (applied) getString(R.string.template_added, row.template.concept, formatMoney(row.template.value)) else getString(R.string.template_no_account)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun delete(template: Template) {
        dataViewModel.deleteTemplate(template.templateId)
        Snackbar.make(binding.root, R.string.template_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { dataViewModel.restoreTemplate(template) }
            .show()
    }

    private class TemplateAdapter(private val onClick: (TemplateRow) -> Unit) : ListAdapter<TemplateRow, TemplateAdapter.TemplateViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            TemplateViewHolder(ItemGroupRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) = holder.bind(getItem(position), onClick)

        class TemplateViewHolder(private val binding: ItemGroupRowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: TemplateRow, onClick: (TemplateRow) -> Unit) = with(binding) {
                val context = root.context
                val color = ContextCompat.getColor(context, if (row.template.value >= 0) R.color.positive else R.color.negative)
                rowAvatarBack.visibility = View.VISIBLE
                rowAvatar.text = row.category?.icon?.takeIf { it.isNotEmpty() } ?: row.template.concept.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
                rowAvatar.setTextColor(color)
                rowAvatarBack.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(color, 0x2E))
                }
                rowTitle.text = row.template.concept
                rowSubtitle.text = listOfNotNull(row.account?.name, row.category?.name).joinToString(" · ")
                rowSubtitle.visibility = if (rowSubtitle.text.isEmpty()) View.GONE else View.VISIBLE
                rowAmount.visibility = View.VISIBLE
                rowAmount.text = formatMoney(row.template.value)
                rowAmount.setTextColor(color)
                rowButton.visibility = View.GONE
                rowRoot.setOnClickListener { onClick(row) }
            }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<TemplateRow>() {
                override fun areItemsTheSame(oldItem: TemplateRow, newItem: TemplateRow) = oldItem.template.templateId == newItem.template.templateId
                override fun areContentsTheSame(oldItem: TemplateRow, newItem: TemplateRow) = oldItem == newItem
            }
        }
    }
}
