// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScheduledDao {
    @Insert
    suspend fun insert(scheduled: ScheduledTransaction): Long

    @Update
    suspend fun update(scheduled: ScheduledTransaction)

    @Query("UPDATE scheduled_table SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Int)

    @Query("DELETE FROM scheduled_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM scheduled_table WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Int)

    @Query("SELECT * FROM scheduled_table WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: Int): List<ScheduledTransaction>

    @Query("SELECT * FROM scheduled_table WHERE id = :id")
    suspend fun get(id: Int): ScheduledTransaction?

    @Query("SELECT * FROM scheduled_table ORDER BY nextDate ASC, id ASC")
    fun observeAll(): LiveData<List<ScheduledTransaction>>

    /** Programados cuya próxima fecha ya ha llegado ([now] en milisegundos). */
    @Query("SELECT * FROM scheduled_table WHERE nextDate <= :now ORDER BY nextDate ASC, id ASC")
    suspend fun getDue(now: Long): List<ScheduledTransaction>

    @Query("SELECT COUNT(*) AS count, MIN(id) AS firstId, MAX(id) AS lastId FROM scheduled_table")
    suspend fun stats(): TableStats
}
