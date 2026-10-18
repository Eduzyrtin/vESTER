// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.datamanager.debtName
import com.sanabria.vester.room.DebtPayment
import com.sanabria.vester.tools.DebtReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sanabria.vester.databinding.ActivityDebtsViewBinding
import com.sanabria.vester.datamanager.AdapterDebt
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.DebtActions
import com.sanabria.vester.room.Debt
import com.sanabria.vester.tools.Tutorial
import com.sanabria.vester.tools.attachSwipeActions
import com.sanabria.vester.tools.TutorialStep

/**
 * Deudas pendientes.
 *
 * Saldar una deuda abre un movimiento ya relleno; la deuda pasa a "completadas" en el mismo momento en que se
 * guarda ese movimiento (si se cancela, la deuda sigue pendiente). Con las casillas se saldan varias de una vez.
 */
class DebtsView : BaseActivity(), DebtActions {

    private lateinit var binding: ActivityDebtsViewBinding
    private val dataViewModel: DataViewModel by viewModels()
    private lateinit var adapter: AdapterDebt
    private var tutorialStarted = false
    private var pdfDebt: Debt? = null
    private var pdfPayments = emptyList<DebtPayment>()

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val debt = pdfDebt ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)?.use { DebtReportPdf.write(this@DebtsView, it, debt, pdfPayments) } }.isSuccess
            }
            Toast.makeText(this@DebtsView, if (ok) R.string.export_done else R.string.export_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebtsViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.debts))

        adapter = AdapterDebt(this)
        binding.recyclerDebt.adapter = adapter
        binding.recyclerDebt.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.debt_columns))
        binding.recyclerDebt.smoothAnimations()
        dataViewModel.activeDebts.observe(this) { showDebts(it) }

        binding.addFab.setOnClickListener { startActivity(Intent(this, MakeDebt::class.java)) }
        // Deslizar a la derecha salda la deuda; a la izquierda la borra (pide confirmación)
        binding.recyclerDebt.attachSwipeActions(
            canSwipe = { true },
            onSwipeLeft = { adapter.currentList.getOrNull(it)?.let(::onDelete) },
            onSwipeRight = { adapter.currentList.getOrNull(it)?.let(::onSettle) },
            rightColor = R.color.positive,
            rightIcon = R.drawable.ic_m_check,
        )
        binding.settleSelected.setOnClickListener { settle(adapter.selectedDebts) }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.debts, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.completedDebts) return super.onOptionsItemSelected(item)
        startActivity(Intent(this, CompletedDebts::class.java))
        return true
    }

    /** Muestra las deudas con el resumen de lo que te deben y lo que debes, o la pantalla vacía si no hay ninguna. */
    private fun showDebts(debts: List<Debt>) {
        adapter.submitList(debts) {
            // El tutorial espera a que la lista esté pintada para poder señalar la casilla de la primera deuda
            if (!tutorialStarted) {
                tutorialStarted = true
                binding.root.post { tutorial() }
            }
        }
        val empty = debts.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.summaryCard.visibility = if (empty) View.GONE else View.VISIBLE
        binding.recyclerDebt.visibility = if (empty) View.GONE else View.VISIBLE
        binding.owedValue.text = formatMoney(debts.filter { it.total > 0 }.sumOf { it.remaining })
        binding.oweValue.text = formatMoney(-debts.filter { it.total < 0 }.sumOf { it.remaining })
    }

    override fun onSettle(debt: Debt) = settle(listOf(debt))

    /** Abre un movimiento con lo que queda por pagar para abonar solo una parte: la deuda sigue pendiente con lo que reste. */
    override fun onPartial(debt: Debt) {
        startActivity(
            MakeTransaction.intent(
                this,
                amount = debt.remaining,
                concept = debt.concept,
                payDebtId = debt.debtId,
            )
        )
    }

    /** Abre un movimiento por la suma de [debts] que, al guardarse, las marca como saldadas. */
    private fun settle(debts: List<Debt>) {
        if (debts.isEmpty()) {
            Toast.makeText(this, R.string.select_debts_first, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(
            MakeTransaction.intent(
                this,
                amount = debts.sumOf { it.remaining },
                concept = debts.joinToString(" / ") { it.concept },
                debtIds = debts.map { it.debtId },
            )
        )
    }

    /** Se borra al momento y un aviso permite deshacerlo (con todo lo que llevaba pagado). */
    override fun onDelete(debt: Debt) {
        dataViewModel.deleteDebt(debt.debtId)
        Snackbar.make(binding.root, R.string.debt_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { dataViewModel.restoreDebt(debt) }
            .show()
    }

    /** Historial de pagos parciales de la deuda, con la opción de sacar su informe en PDF. */
    override fun onDetails(debt: Debt) {
        lifecycleScope.launch {
            val payments = dataViewModel.getDebtPayments(debt.debtId)
            val text = if (payments.isEmpty()) getString(R.string.debt_no_payments) else payments.joinToString(System.lineSeparator()) { "${formatDate(it.date)}   ${formatMoney(it.amount)}" }
            AlertDialog.Builder(this@DebtsView)
                .setTitle(getString(R.string.debt_history_title, debtName(debt)))
                .setMessage(text)
                .setPositiveButton(R.string.debt_export_pdf) { _, _ ->
                    pdfDebt = debt
                    pdfPayments = payments
                    createPdf.launch("vester-" + debtName(debt).filter { it.isLetterOrDigit() } + ".pdf")
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onEdit(debt: Debt) = startActivity(ModifyDebt.intent(this, debt.debtId))

    /** Con deudas marcadas aparece una barra abajo para saldarlas todas de una vez; el botón "Nueva deuda" se aparta. */
    override fun onSelectionChanged(selected: Set<Int>) {
        val any = selected.isNotEmpty()
        binding.selectionBar.visibility = if (any) View.VISIBLE else View.GONE
        if (any) binding.addFab.hide() else binding.addFab.show()
        binding.selectionText.text = resources.getQuantityString(R.plurals.debts_selected, selected.size, selected.size)
    }

    private fun tutorial() {
        if (isFinishing) return
        val firstCard = binding.recyclerDebt.findViewHolderForAdapterPosition(0)?.itemView
        Tutorial.show(
            this, "debts",
            listOf(
                TutorialStep(binding.addFab, R.string.add_debt),
                TutorialStep(firstCard?.findViewById(R.id.selectCheck), R.string.debt_bulk_tutorial),
                TutorialStep(findViewById(R.id.completedDebts), R.string.see_debts),
            ),
        )
    }
}
