// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** Ingresos y gastos de un periodo ("2026-10" o "2026"), en céntimos. Los gastos son negativos. */
data class PeriodTotals(val period: String, val income: Long, val expense: Long)

/** Número de filas de una tabla y sus ids primero y último (null si está vacía). */
data class TableStats(val count: Int, val firstId: Int?, val lastId: Int?)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("SELECT * FROM transaction_table WHERE tId = :id")
    suspend fun get(id: Int): Transaction?

    @Query("SELECT * FROM transaction_table WHERE groupId = :groupId")
    suspend fun getGroup(groupId: Long): List<Transaction>

    /** Deja sin categoría los movimientos de una categoría que se borra. */
    @Query("UPDATE transaction_table SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Int)

    @Query("DELETE FROM transaction_table WHERE tId = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM transaction_table WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Int)

    /** Los [limit] movimientos más recientes de todas las cuentas. */
    @Query("SELECT * FROM transaction_table ORDER BY date DESC, tId DESC LIMIT :limit")
    fun observeAll(limit: Int): LiveData<List<Transaction>>

    /** Los [limit] movimientos más recientes de una cuenta. */
    @Query("SELECT * FROM transaction_table WHERE accountId = :accountId ORDER BY date DESC, tId DESC LIMIT :limit")
    fun observeByAccount(accountId: Int, limit: Int): LiveData<List<Transaction>>

    @Query("SELECT * FROM transaction_table WHERE accountId = :accountId ORDER BY date DESC, tId DESC")
    suspend fun getByAccount(accountId: Int): List<Transaction>

    /** Movimientos con fecha en [from, to), en milisegundos. */
    @Query("SELECT * FROM transaction_table WHERE date >= :from AND date < :to ORDER BY date DESC, tId DESC")
    fun observeBetween(from: Long, to: Long): LiveData<List<Transaction>>

    /** El movimiento más reciente de cada cuenta (las cuentas sin movimientos no aparecen). */
    @Query(
        """SELECT * FROM transaction_table t1 WHERE t1.tId = (
            SELECT t2.tId FROM transaction_table t2 WHERE t2.accountId = t1.accountId
            ORDER BY t2.date DESC, t2.tId DESC LIMIT 1)"""
    )
    fun observeLastPerAccount(): LiveData<List<Transaction>>

    /**
     * Ingresos y gastos agrupados por periodo, del más reciente al más antiguo. Las transferencias no cuentan.
     *
     * @param format Formato de strftime del periodo: "%Y-%m" para meses o "%Y" para años.
     * @param accountId Cuenta a la que limitarse, o null para todas.
     */
    @Query(
        """SELECT strftime(:format, date / 1000, 'unixepoch', 'localtime') AS period,
            SUM(CASE WHEN value > 0 THEN value ELSE 0 END) AS income,
            SUM(CASE WHEN value < 0 THEN value ELSE 0 END) AS expense
            FROM transaction_table
            WHERE isTransfer = 0 AND (:accountId IS NULL OR accountId = :accountId)
            GROUP BY period ORDER BY period DESC"""
    )
    fun observeTotals(format: String, accountId: Int?): LiveData<List<PeriodTotals>>

    @Query("SELECT COUNT(*) AS count, MIN(tId) AS firstId, MAX(tId) AS lastId FROM transaction_table")
    suspend fun stats(): TableStats

    /** Todos los movimientos, del más antiguo al más reciente (para exportarlos). */
    @Query("SELECT * FROM transaction_table ORDER BY date ASC, tId ASC")
    suspend fun getAll(): List<Transaction>

    /** Movimientos con fecha en [from, to), del más antiguo al más reciente (para los informes en PDF). */
    @Query("SELECT * FROM transaction_table WHERE date >= :from AND date < :to ORDER BY date ASC, tId ASC")
    suspend fun getBetween(from: Long, to: Long): List<Transaction>

    /**
     * Busca movimientos. Cada filtro nulo se ignora.
     *
     * @param kind 0 todos, 1 pagos, 2 ingresos, 3 transferencias.
     * @param minAbs Importe mínimo en céntimos (sin signo); [maxAbs] el máximo.
     */
    @Query(
        """SELECT * FROM transaction_table WHERE
            (:text IS NULL OR concept LIKE '%' || :text || '%' OR note LIKE '%' || :text || '%')
            AND (:accountId IS NULL OR accountId = :accountId)
            AND (:categoryId IS NULL OR categoryId = :categoryId)
            AND (:from IS NULL OR date >= :from)
            AND (:to IS NULL OR date < :to)
            AND (:minAbs IS NULL OR ABS(value) >= :minAbs)
            AND (:maxAbs IS NULL OR ABS(value) <= :maxAbs)
            AND (:kind = 0 OR (:kind = 1 AND value < 0 AND isTransfer = 0)
                 OR (:kind = 2 AND value > 0 AND isTransfer = 0) OR (:kind = 3 AND isTransfer = 1))
            ORDER BY date DESC, tId DESC LIMIT 500"""
    )
    fun search(
        text: String?, accountId: Int?, categoryId: Int?, from: Long?, to: Long?, minAbs: Long?, maxAbs: Long?, kind: Int,
    ): LiveData<List<Transaction>>

    /** Conceptos usados, del más reciente al más antiguo y sin repetir (para autocompletar). */
    @Query("SELECT concept FROM transaction_table WHERE isTransfer = 0 GROUP BY concept ORDER BY MAX(date) DESC LIMIT :limit")
    suspend fun recentConcepts(limit: Int): List<String>

    /** El último movimiento con ese concepto (sin distinguir mayúsculas), para proponer su importe y su categoría. */
    @Query("SELECT * FROM transaction_table WHERE concept = :concept COLLATE NOCASE AND isTransfer = 0 ORDER BY date DESC, tId DESC LIMIT 1")
    suspend fun lastByConcept(concept: String): Transaction?

    /** Nombres de las fotos de tickets que usan los movimientos. */
    @Query("SELECT photo FROM transaction_table WHERE photo != ''")
    suspend fun photos(): List<String>

    /** Lo que ha cambiado el saldo de una cuenta desde [from] (milisegundos): ingresos menos pagos, transferencias incluidas. */
    @Query("SELECT COALESCE(SUM(value), 0) FROM transaction_table WHERE accountId = :accountId AND date >= :from")
    suspend fun netSince(accountId: Int, from: Long): Long

    @Query("SELECT MIN(date) FROM transaction_table")
    suspend fun firstDate(): Long?
}
