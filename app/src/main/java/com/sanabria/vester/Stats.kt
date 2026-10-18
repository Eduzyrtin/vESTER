// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityStatsBinding
import com.sanabria.vester.datamanager.AppStats
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.AppDatabase
import com.sanabria.vester.room.TableStats
import kotlinx.coroutines.launch

/**
 * Estadísticas de la base de datos: cuántas cuentas, transacciones, deudas, programadas y sugerencias hay
 * y qué ids ocupan (útil para revisar copias de seguridad o depurar), más el saldo total y la versión.
 */
class Stats : BaseActivity() {

    private lateinit var binding: ActivityStatsBinding
    private var current: LinearLayout? = null
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbarStats, getString(R.string.stats))
        lifecycleScope.launch { show(dataViewModel.stats()) }
    }

    private fun show(stats: AppStats) {
        section(R.string.stats_data)
        table(R.string.stats_accounts, stats.accounts)
        table(R.string.stats_transactions, stats.transactions)
        stats.firstTransaction?.let { row(getString(R.string.stats_first_transaction), formatDate(it)) }
        table(R.string.stats_debts, stats.debts)
        row(getString(R.string.stats_active_debts), stats.activeDebts.toString())
        table(R.string.stats_scheduled, stats.scheduled)
        table(R.string.stats_suggestions, stats.suggestions)

        section(R.string.stats_money)
        row(getString(R.string.stats_total_balance), stats.totalBalance.let { total -> total.currency?.let { formatMoney(total.cents, it) } ?: formatMoney(total.cents) })

        section(R.string.stats_app)
        row(getString(R.string.stats_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        row(getString(R.string.stats_database), AppDatabase.VERSION.toString())
    }

    /** Número de filas y, si hay, el rango de ids. */
    private fun table(label: Int, stats: TableStats) {
        row(getString(label), stats.count.toString())
        if (stats.firstId != null && stats.lastId != null) {
            row(getString(R.string.stats_ids), getString(R.string.stats_id_range, stats.firstId, stats.lastId), indent = true)
        }
    }

    /** Empieza un grupo: una tarjeta con su título; las filas siguientes se añaden dentro. */
    private fun section(title: Int) {
        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * density).toInt()
            setPadding(padding, (12 * density).toInt(), padding, (8 * density).toInt())
        }
        content.addView(TextView(this).apply {
            setText(title)
            setTextAppearance(R.style.TextAppearance_App_Title)
            setTextColor(ContextCompat.getColor(context, R.color.primaryText))
        })
        val card = MaterialCardView(this).apply {
            radius = 16 * density
            cardElevation = 0f
            strokeWidth = density.toInt()
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (12 * density).toInt() }
            addView(content)
        }
        binding.statsContainer.addView(card)
        current = content
    }

    private fun row(label: String, value: String, indent: Boolean = false) {
        val secondary = ContextCompat.getColor(this, R.color.secondaryText)
        val primary = ContextCompat.getColor(this, R.color.primaryText)
        val density = resources.displayMetrics.density
        current?.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ((if (indent) 32 else 44) * density).toInt()
            addView(TextView(context).apply {
                text = label
                setTextColor(secondary)
                if (indent) setPadding((16 * density).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_App_Amount)
                setTextColor(if (indent) secondary else primary)
                if (indent) textSize = 14f
            })
        })
    }
}
