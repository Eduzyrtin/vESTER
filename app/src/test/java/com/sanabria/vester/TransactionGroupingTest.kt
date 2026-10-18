// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.datamanager.AdapterTransaction
import com.sanabria.vester.datamanager.TransactionItem
import com.sanabria.vester.datamanager.TransactionRow
import com.sanabria.vester.room.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** La lista de movimientos se agrupa por día, con el total de cada día sin contar las transferencias. */
class TransactionGroupingTest {

    private fun at(daysAgo: Int, hour: Int): Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun item(id: Int, value: Long, date: Date, transfer: Boolean = false) =
        TransactionItem(Transaction(transactionId = id, accountId = 1, concept = "c$id", value = value, date = date, isTransfer = transfer), null)

    @Test
    fun emptyListHasNoRows() {
        assertTrue(AdapterTransaction.groupByDay(emptyList()).isEmpty())
    }

    @Test
    fun oneHeaderPerDayBeforeItsTransactions() {
        val rows = AdapterTransaction.groupByDay(
            listOf(item(1, -500, at(0, 18)), item(2, 1000, at(0, 9)), item(3, -250, at(2, 12)))
        )
        assertEquals(5, rows.size)
        assertTrue(rows[0] is TransactionRow.Header)
        assertTrue(rows[1] is TransactionRow.Entry && rows[2] is TransactionRow.Entry)
        assertTrue(rows[3] is TransactionRow.Header)
        assertTrue(rows[4] is TransactionRow.Entry)
        assertEquals(500L, (rows[0] as TransactionRow.Header).total)
        assertEquals(-250L, (rows[3] as TransactionRow.Header).total)
    }

    @Test
    fun transfersDoNotCountInTheDayTotal() {
        val rows = AdapterTransaction.groupByDay(
            listOf(item(1, -2000, at(0, 11), transfer = true), item(2, 2000, at(0, 11), transfer = true), item(3, -300, at(0, 10)))
        )
        assertEquals(-300L, (rows.first() as TransactionRow.Header).total)
    }
}
