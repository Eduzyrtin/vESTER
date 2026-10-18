// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sanabria.vester.datamanager.AdapterTransaction
import com.sanabria.vester.tools.attachSwipeActions
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Transaction

/**
 * Opciones de un movimiento al mantenerlo pulsado: editarlo, duplicarlo, borrarlo devolviendo o no su importe al saldo y,
 * si forma parte de una operación enlazada (una transferencia o un gasto pagado desde varias cuentas),
 * borrar la operación entera.
 */
fun AppCompatActivity.showTransactionOptions(transaction: Transaction, dataViewModel: DataViewModel) {
    val actions = mutableListOf<Pair<String, () -> Unit>>(
        getString(R.string.edit_transaction) to {
            startActivity(ModifyTransaction.intent(this, transaction.transactionId))
        },
        getString(R.string.duplicate) to {
            // Abre el formulario de un movimiento nuevo con los mismos datos
            startActivity(
                MakeTransaction.intent(this, accountId = transaction.accountId, amount = transaction.value, concept = transaction.concept)
            )
        },
        getString(R.string.delete_and_revert) to {
            dataViewModel.deleteTransaction(transaction.transactionId, revertBalance = true)
        },
        getString(R.string.delete_keep_balance) to {
            dataViewModel.deleteTransaction(transaction.transactionId, revertBalance = false)
        },
    )
    transaction.groupId?.let { group ->
        val label = if (transaction.isTransfer) R.string.delete_whole_transfer else R.string.delete_whole_group
        actions += getString(label) to { dataViewModel.deleteGroup(group, revertBalance = true) }
    }
    AlertDialog.Builder(this)
        .setTitle(transaction.concept)
        .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/**
 * Gestos de deslizar sobre la lista de movimientos: a la izquierda se borra (devolviendo el saldo) con un aviso para
 * deshacerlo y a la derecha se edita. Los movimientos enlazados (transferencias y gastos repartidos) abren sus
 * opciones, porque ahí hay que decidir si se borra la operación entera.
 */
fun AppCompatActivity.setupTransactionSwipe(recycler: RecyclerView, adapter: AdapterTransaction, dataViewModel: DataViewModel) {
    recycler.attachSwipeActions(
        canSwipe = { adapter.transactionAt(it) != null },
        onSwipeLeft = { position ->
            adapter.transactionAt(position)?.let { transaction ->
                if (transaction.groupId != null) {
                    showTransactionOptions(transaction, dataViewModel)
                } else {
                    dataViewModel.deleteTransaction(transaction.transactionId, revertBalance = true)
                    Snackbar.make(recycler, R.string.transaction_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { dataViewModel.restoreTransaction(transaction) }
                        .show()
                }
            }
        },
        onSwipeRight = { position ->
            adapter.transactionAt(position)?.let { startActivity(ModifyTransaction.intent(this, it.transactionId)) }
        },
    )
}
