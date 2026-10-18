// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.databinding.ActivityCategoriesBinding
import com.sanabria.vester.databinding.ItemCategoryBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.combine
import com.sanabria.vester.room.Category
import com.sanabria.vester.tools.AppSettings
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.smoothAnimations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fila de la lista de categorías: lo gastado en el mes ([category] null = movimientos sin categoría). */
data class CategoryRow(val category: Category?, val spent: Long)

/**
 * Categorías de los movimientos con lo gastado en cada una durante el mes elegido y la barra de su presupuesto
 * mensual (si lo tienen). Al tocar una se edita; el botón crea otra.
 */
class Categories : BaseActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.categories))

        val adapter = CategoryAdapter { row -> row.category?.let { startActivity(CategoryForm.intent(this, it.categoryId)) } }
        binding.recyclerCategories.adapter = adapter
        binding.recyclerCategories.layoutManager = LinearLayoutManager(this)
        binding.recyclerCategories.smoothAnimations()
        binding.emptyView.bindEmpty(R.drawable.ic_m_label, R.string.no_categories_title, R.string.no_categories_hint)

        binding.previousMonth.setOnClickListener { dataViewModel.shiftCategoryMonth(-1) }
        binding.nextMonth.setOnClickListener { dataViewModel.shiftCategoryMonth(+1) }
        binding.fab.setOnClickListener { startActivity(CategoryForm.intent(this)) }

        dataViewModel.categoryMonthStart.observe(this) { start ->
            binding.monthLabel.text = if (AppSettings.monthStartDay == 1) {
                SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(start))
                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            } else {
                // El mes no empieza el día 1: se enseña el periodo, por ejemplo "25 sept - 24 oct"
                DateUtils.formatDateRange(this, start, nextMonth(start) - 1, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
            }
        }
        combine(dataViewModel.categories, dataViewModel.categorySpending) { categories, spending ->
            val spentById = spending.associate { it.categoryId to it.spent }
            val rows = categories.map { CategoryRow(it, spentById[it.categoryId] ?: 0L) }
            val uncategorized = spentById[null] ?: 0L
            if (uncategorized > 0) rows + CategoryRow(null, uncategorized) else rows
        }.observe(this) { rows ->
            adapter.submitList(rows)
            binding.emptyView.showWhen(rows.isEmpty())
            showSummary(rows)
        }
    }

    /** "Gastado: 320,00 · Presupuestado: 500,00" (lo segundo solo si alguna categoría tiene presupuesto). */
    private fun showSummary(rows: List<CategoryRow>) {
        val spent = rows.sumOf { it.spent }
        val budget = rows.sumOf { it.category?.monthlyBudget ?: 0L }
        binding.monthSummary.text = if (budget > 0) {
            getString(R.string.month_spent_budget, formatMoney(spent), formatMoney(budget))
        } else {
            getString(R.string.month_spent, formatMoney(spent))
        }
    }

    private class CategoryAdapter(private val onClick: (CategoryRow) -> Unit) :
        ListAdapter<CategoryRow, CategoryAdapter.CategoryViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CategoryViewHolder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) = holder.bind(getItem(position), onClick)

        class CategoryViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: CategoryRow, onClick: (CategoryRow) -> Unit) = with(binding) {
                val context = root.context
                val category = row.category
                val color = category?.color ?: ContextCompat.getColor(context, R.color.tile_teal)
                categoryIcon.text = category?.icon?.ifEmpty { "🏷️" } ?: "🏷️"
                categoryIcon.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(color, 0x33))
                }
                categoryName.text = category?.name ?: context.getString(R.string.uncategorized)
                categorySpent.text = formatMoney(row.spent)

                val budget = category?.monthlyBudget ?: 0L
                if (budget > 0) {
                    val ratio = row.spent.toDouble() / budget
                    categoryProgress.visibility = android.view.View.VISIBLE
                    categoryProgress.setProgressCompat((ratio * 100).toInt().coerceIn(0, 100), true)
                    categoryProgress.setIndicatorColor(
                        ContextCompat.getColor(
                            context,
                            when {
                                ratio > 1.0 -> R.color.negative
                                ratio > 0.8 -> R.color.tile_orange
                                else -> R.color.positive
                            },
                        )
                    )
                    categoryStatus.text = if (row.spent <= budget) {
                        context.getString(R.string.budget_left, formatMoney(budget - row.spent), formatMoney(budget))
                    } else {
                        context.getString(R.string.budget_over, formatMoney(row.spent - budget))
                    }
                } else {
                    categoryProgress.visibility = android.view.View.GONE
                    categoryStatus.text = context.getString(if (category == null) R.string.uncategorized_hint else R.string.no_budget)
                }
                categoryCard.setOnClickListener { onClick(row) }
                categoryCard.isClickable = category != null
            }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<CategoryRow>() {
                override fun areItemsTheSame(oldItem: CategoryRow, newItem: CategoryRow) =
                    oldItem.category?.categoryId == newItem.category?.categoryId

                override fun areContentsTheSame(oldItem: CategoryRow, newItem: CategoryRow) = oldItem == newItem
            }
        }
    }
}
