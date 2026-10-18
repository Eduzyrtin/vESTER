// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Query("DELETE FROM goal_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM goal_table WHERE id = :id")
    suspend fun get(id: Int): Goal?

    @Query("SELECT * FROM goal_table ORDER BY id ASC")
    fun observeAll(): LiveData<List<Goal>>

    /** Suma lo ahorrado a mano en una meta. */
    @Query("UPDATE goal_table SET saved = saved + :amount WHERE id = :id")
    suspend fun addSaved(id: Int, amount: Long)

    /** Las metas enlazadas a una cuenta que se borra se quedan sin enlace (y con lo que llevaban ahorrado en ese momento). */
    @Query("UPDATE goal_table SET accountId = NULL, saved = :saved WHERE accountId = :accountId")
    suspend fun unlinkAccount(accountId: Int, saved: Long)
}
