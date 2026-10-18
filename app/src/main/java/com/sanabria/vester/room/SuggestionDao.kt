// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SuggestionDao {
    /** Guarda la sugerencia salvo que ya exista una de la misma notificación. Devuelve -1 si se ignora. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: Suggestion): Long

    @Query("DELETE FROM suggestion_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM suggestion_table ORDER BY date DESC, id DESC")
    fun observeAll(): LiveData<List<Suggestion>>

    @Query("SELECT COUNT(*) FROM suggestion_table")
    fun observeCount(): LiveData<Int>

    @Query("SELECT * FROM suggestion_table WHERE id = :id")
    suspend fun get(id: Int): Suggestion?

    @Query("SELECT COUNT(*) AS count, MIN(id) AS firstId, MAX(id) AS lastId FROM suggestion_table")
    suspend fun stats(): TableStats
}
