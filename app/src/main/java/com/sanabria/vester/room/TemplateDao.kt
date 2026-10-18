// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(template: Template): Long

    @Query("DELETE FROM template_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM template_table WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Int)

    @Query("SELECT * FROM template_table WHERE id = :id")
    suspend fun get(id: Int): Template?

    @Query("SELECT * FROM template_table ORDER BY concept COLLATE NOCASE ASC, id ASC")
    fun observeAll(): LiveData<List<Template>>
}
