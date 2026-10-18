// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import com.sanabria.vester.room.GroupExpense

/** Pago sugerido para dejar las cuentas a cero: [from] le da [amount] céntimos a [to] (ids de miembro). */
data class Transfer(val from: Int, val to: Int, val amount: Long)

/**
 * Cuentas de un grupo de gastos compartidos. Son funciones puras sobre los gastos, fáciles de probar.
 */
object GroupBalances {

    /**
     * Saldo de cada miembro en céntimos: positivo si le deben dinero (puso más de lo que le tocaba) y negativo si debe.
     * Cada gasto se reparte entre sus participantes (a partes iguales o por partes); el céntimo que sobra se da a los primeros de la
     * lista, así que la suma de todos los saldos es siempre exactamente 0.
     */
    fun balances(expenses: List<GroupExpense>, memberIds: Collection<Int>): Map<Int, Long> {
        val result = memberIds.associateWith { 0L }.toMutableMap()
        for (expense in expenses) {
            val parts = expense.participantParts.distinctBy { it.first }
            if (parts.isEmpty()) continue
            result[expense.payerId] = (result[expense.payerId] ?: 0L) + expense.amount
            val totalParts = parts.sumOf { it.second }.toLong()
            val shares = parts.map { expense.amount * it.second / totalParts }
            // Los céntimos que sobran del redondeo se dan de uno en uno a los primeros
            val extra = (expense.amount - shares.sum()).toInt()
            parts.forEachIndexed { index, (id, _) ->
                val cents = shares[index] + if (index < extra) 1 else 0
                result[id] = (result[id] ?: 0L) - cents
            }
        }
        return result
    }

    /**
     * Pagos que dejan a todos a cero con el menor número posible de transferencias (reparto voraz: el que más debe
     * paga al que más se le debe, y así hasta acabar).
     */
    fun settlements(balances: Map<Int, Long>): List<Transfer> {
        val creditors = balances.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val transfers = mutableListOf<Transfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditor, owed) = creditors.first()
            val (debtor, debt) = debtors.first()
            val amount = minOf(owed, debt)
            transfers += Transfer(from = debtor, to = creditor, amount = amount)
            if (owed == amount) creditors.removeAt(0) else creditors[0] = creditor to owed - amount
            if (debt == amount) debtors.removeAt(0) else debtors[0] = debtor to debt - amount
            creditors.sortByDescending { it.second }
            debtors.sortByDescending { it.second }
        }
        return transfers
    }
}
