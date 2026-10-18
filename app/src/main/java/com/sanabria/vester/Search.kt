// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sanabria.vester.databinding.ActivitySearchBinding
import com.sanabria.vester.databinding.SheetSearchFiltersBinding
import com.sanabria.vester.datamanager.AdapterTransaction
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.SearchFilter
import com.sanabria.vester.datamanager.SearchKind
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.smoothAnimations
import kotlinx.coroutines.launch

/**
 * Busca movimientos mientras se escribe: por texto (concepto o nota) y tipo, y con la hoja de filtros por cuenta,
 * categoría, rango de fechas e importe. Las filas se pueden editar y borrar como en cualquier otra lista.
 */
class Search : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var filter = SearchFilter()
    private var accounts = emptyList<Account>()
    private var categories = emptyList<Category>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.search))

        val adapter = AdapterTransaction { showTransactionOptions(it, dataViewModel) }
        binding.recyclerResults.adapter = adapter
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.smoothAnimations()
        setupTransactionSwipe(binding.recyclerResults, adapter, dataViewModel)
        binding.emptyView.bindEmpty(R.drawable.ic_m_search, R.string.search_none, R.string.search_none_hint)

        binding.kindChips.setItems(
            listOf(R.string.search_all, R.string.payment, R.string.income, R.string.transfer).map { getString(it) },
            selected = filter.kind.ordinal,
        )
        binding.kindChips.setOnCheckedStateChangeListener { group, _ -> update(filter.copy(kind = SearchKind.entries[group.selectedIndex])) }
        binding.searchInput.doAfterTextChanged { update(filter.copy(text = it?.toString())) }
        binding.filtersButton.setOnClickListener { showFilters() }

        lifecycleScope.launch {
            accounts = dataViewModel.everyAccount()
            categories = dataViewModel.categoriesOnce()
        }
        dataViewModel.searchResults.observe(this) { items ->
            adapter.submit(items)
            binding.emptyView.showWhen(items.isEmpty())
            val net = items.filter { !it.transaction.isTransfer }.sumOf { it.transaction.value }
            binding.summary.text = if (items.isEmpty()) "" else getString(R.string.search_summary, items.size, formatMoney(net))
        }
        update(filter)
    }

    private fun update(newFilter: SearchFilter) {
        filter = newFilter
        dataViewModel.setSearch(filter)
        val advanced = listOfNotNull(filter.accountId, filter.categoryId, filter.from, filter.to, filter.minAbs, filter.maxAbs).size
        binding.filtersButton.text = if (advanced == 0) getString(R.string.search_filters) else getString(R.string.search_filters_count, advanced)
    }

    /** Hoja con los filtros avanzados: se aplican al pulsar "Aplicar". */
    private fun showFilters() {
        val sheet = BottomSheetDialog(this)
        val view = SheetSearchFiltersBinding.inflate(layoutInflater)
        sheet.setContentView(view.root)

        view.accountField.setItems(listOf(getString(R.string.search_any_account)) + accounts.map { it.name })
        view.accountField.setSelection(accounts.indexOfFirst { it.accountId == filter.accountId } + 1)
        view.categoryField.setItems(listOf(getString(R.string.search_any_category)) + categories.map { it.label })
        view.categoryField.setSelection(categories.indexOfFirst { it.categoryId == filter.categoryId } + 1)
        val from = view.fromInput.bindOptionalDatePicker(filter.from)
        // "Hasta" incluye ese día entero: se guarda como el inicio del día siguiente
        val to = view.toInput.bindOptionalDatePicker(filter.to?.let { it - 1 })
        filter.minAbs?.let { view.minInput.setText(formatMoneyInput(it)) }
        filter.maxAbs?.let { view.maxInput.setText(formatMoneyInput(it)) }
        view.minInput.setupMoneyInput()
        view.maxInput.setupMoneyInput()

        view.applyFilters.setOnClickListener {
            update(
                filter.copy(
                    accountId = accounts.getOrNull(view.accountField.selectedItemPosition - 1)?.accountId,
                    categoryId = categories.getOrNull(view.categoryField.selectedItemPosition - 1)?.categoryId,
                    from = from()?.let { startOfDay(it) },
                    to = to()?.let { nextDay(startOfDay(it)) },
                    minAbs = parseMoney(view.minInput.text)?.takeIf { it > 0 },
                    maxAbs = parseMoney(view.maxInput.text)?.takeIf { it > 0 },
                )
            )
            sheet.dismiss()
        }
        view.resetFilters.setOnClickListener {
            update(filter.copy(accountId = null, categoryId = null, from = null, to = null, minAbs = null, maxAbs = null))
            sheet.dismiss()
        }
        sheet.show()
    }
}
