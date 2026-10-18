// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    /** Cuentas que se ven (sin archivar), en el orden elegido por el usuario. */
    @Query("SELECT * FROM account_table WHERE archived = 0 ORDER BY position ASC, id ASC")
    fun observeAll(): LiveData<List<Account>>

    /** Todas las cuentas, también las archivadas (para poner nombre a sus movimientos). */
    @Query("SELECT * FROM account_table ORDER BY position ASC, id ASC")
    fun observeEvery(): LiveData<List<Account>>

    @Query("SELECT * FROM account_table WHERE archived = 1 ORDER BY position ASC, id ASC")
    fun observeArchived(): LiveData<List<Account>>

    @Query("SELECT * FROM account_table WHERE archived = 0 ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<Account>

    /** Todas las cuentas, también las archivadas (para exportar). */
    @Query("SELECT * FROM account_table ORDER BY position ASC, id ASC")
    suspend fun getEvery(): List<Account>

    @Query("SELECT * FROM account_table WHERE id = :id")
    fun observe(id: Int): LiveData<Account?>

    @Query("SELECT * FROM account_table WHERE id = :id")
    suspend fun get(id: Int): Account?

    /** Cuentas con ese id (para restaurar una borrada sin pisar otra). */
    @Query("SELECT COUNT(*) FROM account_table WHERE id = :id")
    suspend fun countById(id: Int): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM account_table")
    suspend fun maxPosition(): Int

    /** Suma (o resta, si es negativo) [amount] céntimos al saldo directamente en SQL, sin leer el valor antes. */
    @Query("UPDATE account_table SET total = total + :amount WHERE id = :id")
    suspend fun addToTotal(id: Int, amount: Long)

    @Query("UPDATE account_table SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Int, position: Int)

    @Query("DELETE FROM account_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) AS count, MIN(id) AS firstId, MAX(id) AS lastId FROM account_table")
    suspend fun stats(): TableStats

    @Query("SELECT COALESCE(SUM(total), 0) FROM account_table WHERE archived = 0")
    suspend fun totalBalance(): Long
}
