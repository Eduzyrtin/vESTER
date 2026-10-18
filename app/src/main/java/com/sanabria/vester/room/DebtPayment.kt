// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/** Pago parcial de una deuda: [amount] céntimos (positivos) abonados en [date]. Se borra con la deuda. */
@Entity(
    tableName = "debt_payment_table",
    foreignKeys = [ForeignKey(entity = Debt::class, parentColumns = ["debtId"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("debtId")],
)
data class DebtPayment(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val paymentId: Int = 0,

    @ColumnInfo(name = "debtId")
    val debtId: Int,

    @ColumnInfo(name = "amount")
    val amount: Long,

    @ColumnInfo(name = "date")
    val date: Date,
)
