// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanabria.vester.databinding.ActivityScheduledBinding
import com.sanabria.vester.datamanager.AdapterScheduled
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.ScheduledItem
import com.sanabria.vester.tools.attachSwipeActions
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen

/**
 * Movimientos programados y recurrentes pendientes, ordenados por la próxima fecha.
 * Se crean desde el formulario de transacción eligiendo una fecha futura o una repetición.
 */
class ScheduledTransactions : BaseActivity() {

    private lateinit var binding: ActivityScheduledBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduledBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbarScheduled, getString(R.string.scheduled))

        val adapter = AdapterScheduled(onClick = { item -> edit(item) })
        binding.emptyView.bindEmpty(R.drawable.ic_m_clock, R.string.no_scheduled_title, R.string.no_scheduled_hint)
        binding.recyclerScheduled.adapter = adapter
        binding.recyclerScheduled.layoutManager = LinearLayoutManager(this)
        binding.recyclerScheduled.smoothAnimations()
        // Mismos gestos que en los movimientos: a la derecha se edita y a la izquierda se borra (pidiendo confirmación)
        binding.recyclerScheduled.attachSwipeActions(
            canSwipe = { it in 0 until adapter.itemCount },
            onSwipeLeft = { confirmDelete(adapter.currentList[it]) },
            onSwipeRight = { edit(adapter.currentList[it]) },
        )
        dataViewModel.scheduled.observe(this) { items ->
            adapter.submitList(items)
            binding.emptyView.showWhen(items.isEmpty())
        }

        binding.fabScheduled.setOnClickListener { startActivity(MakeTransaction.intent(this)) }
    }

    private fun edit(item: ScheduledItem) = startActivity(MakeTransaction.scheduledIntent(this, item.scheduled.id))

    private fun confirmDelete(item: ScheduledItem) {
        AlertDialog.Builder(this)
            .setTitle(item.scheduled.concept)
            .setMessage(R.string.delete_scheduled_sure)
            .setPositiveButton(R.string.yes) { _, _ -> dataViewModel.deleteScheduled(item.scheduled.id) }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
