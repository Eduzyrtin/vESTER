// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface GroupDao {
    @Insert
    suspend fun insertGroup(group: ExpenseGroup): Long

    @Update
    suspend fun updateGroup(group: ExpenseGroup)

    /** Borrar un grupo borra también sus miembros y sus gastos (en cascada). */
    @Query("DELETE FROM group_table WHERE id = :id")
    suspend fun deleteGroup(id: Int)

    @Query("SELECT * FROM group_table WHERE id = :id")
    suspend fun getGroup(id: Int): ExpenseGroup?

    @Query("SELECT * FROM group_table WHERE id = :id")
    fun observeGroup(id: Int): LiveData<ExpenseGroup?>

    @Query("SELECT * FROM group_table ORDER BY created DESC, id DESC")
    fun observeGroups(): LiveData<List<ExpenseGroup>>

    @Insert
    suspend fun insertMember(member: GroupMember): Long

    @Query("DELETE FROM group_member_table WHERE id = :id")
    suspend fun deleteMember(id: Int)

    @Query("SELECT * FROM group_member_table ORDER BY id ASC")
    fun observeAllMembers(): LiveData<List<GroupMember>>

    @Query("SELECT * FROM group_member_table WHERE groupId = :groupId ORDER BY id ASC")
    fun observeMembers(groupId: Int): LiveData<List<GroupMember>>

    @Query("SELECT * FROM group_member_table WHERE groupId = :groupId ORDER BY id ASC")
    suspend fun getMembers(groupId: Int): List<GroupMember>

    @Insert
    suspend fun insertExpense(expense: GroupExpense): Long

    @Update
    suspend fun updateExpense(expense: GroupExpense)

    @Query("DELETE FROM group_expense_table WHERE id = :id")
    suspend fun deleteExpense(id: Int)

    @Query("SELECT * FROM group_expense_table WHERE id = :id")
    suspend fun getExpense(id: Int): GroupExpense?

    @Query("SELECT * FROM group_expense_table WHERE groupId = :groupId ORDER BY date DESC, id DESC")
    fun observeExpenses(groupId: Int): LiveData<List<GroupExpense>>

    @Query("SELECT * FROM group_expense_table")
    fun observeAllExpenses(): LiveData<List<GroupExpense>>

    @Query("SELECT * FROM group_expense_table WHERE groupId = :groupId")
    suspend fun getExpenses(groupId: Int): List<GroupExpense>
}
