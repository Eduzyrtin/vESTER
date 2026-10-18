// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DebtDao {
    @Insert
    suspend fun insert(debt: Debt): Long

    @Update
    suspend fun update(debt: Debt)

    @Query("SELECT * FROM debt_table WHERE debtId = :id")
    suspend fun get(id: Int): Debt?

    @Query("DELETE FROM debt_table WHERE debtId = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) AS count, MIN(debtId) AS firstId, MAX(debtId) AS lastId FROM debt_table")
    suspend fun stats(): TableStats

    /** Deudas pendientes con fecha límite (para los avisos de vencimiento). */
    @Query("SELECT * FROM debt_table WHERE isActive = 1 AND dueDate IS NOT NULL")
    suspend fun getActiveWithDueDate(): List<Debt>

    @Insert
    suspend fun insertPayment(payment: DebtPayment): Long

    @Query("SELECT * FROM debt_payment_table WHERE debtId = :debtId ORDER BY date ASC, id ASC")
    suspend fun getPayments(debtId: Int): List<DebtPayment>

    @Query("SELECT COUNT(*) FROM debt_table WHERE isActive = 1")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM debt_table WHERE isActive = 1 ORDER BY debtDate DESC, debtId DESC")
    fun observeActive(): LiveData<List<Debt>>

    @Query("SELECT * FROM debt_table WHERE isActive = 0 ORDER BY debtCompleted DESC, debtId DESC")
    fun observeCompleted(): LiveData<List<Debt>>

    /** Deudas (pendientes o no) con fecha en [from, to), en milisegundos. */
    @Query("SELECT * FROM debt_table WHERE debtDate >= :from AND debtDate < :to ORDER BY debtDate DESC, debtId DESC")
    fun observeBetween(from: Long, to: Long): LiveData<List<Debt>>

    /** Marca como saldadas las deudas [ids] con fecha [completed] en milisegundos. */
    @Query("UPDATE debt_table SET isActive = 0, debtCompleted = :completed WHERE debtId IN (:ids)")
    suspend fun complete(ids: List<Int>, completed: Long)
}
