// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** Gasto de un periodo en una categoría (null = sin categoría), en céntimos y en positivo. */
data class CategorySpending(val categoryId: Int?, val spent: Long)

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM category_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM category_table WHERE id = :id")
    suspend fun get(id: Int): Category?

    @Query("SELECT * FROM category_table ORDER BY position ASC, id ASC")
    fun observeAll(): LiveData<List<Category>>

    @Query("SELECT * FROM category_table ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT COUNT(*) FROM category_table")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM category_table")
    suspend fun maxPosition(): Int

    /** Gasto (sin transferencias) en [from, to) agrupado por categoría. */
    @Query(
        """SELECT categoryId AS categoryId, -SUM(value) AS spent FROM transaction_table
            WHERE isTransfer = 0 AND value < 0 AND date >= :from AND date < :to
            GROUP BY categoryId"""
    )
    fun observeSpending(from: Long, to: Long): LiveData<List<CategorySpending>>

    /** Gasto de una categoría en [from, to), para avisar de que se pasa del presupuesto. */
    @Query(
        """SELECT COALESCE(-SUM(value), 0) FROM transaction_table
            WHERE categoryId = :categoryId AND isTransfer = 0 AND value < 0 AND date >= :from AND date < :to"""
    )
    suspend fun spentIn(categoryId: Int, from: Long, to: Long): Long
}
