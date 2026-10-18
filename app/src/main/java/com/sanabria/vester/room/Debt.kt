// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Deuda pendiente o saldada.
 *
 * @property total Importe en céntimos: positivo si nos lo deben (a cobrar) y negativo si lo debemos (a pagar).
 * @property isActive true mientras está pendiente.
 * @property completed Fecha en la que se saldó. Mientras está activa no tiene sentido y vale [NOT_COMPLETED].
 * @property date Fecha de la deuda.
 * @property person Persona con la que es la deuda (vacío si no se ha puesto: entonces se deduce del concepto).
 * @property dueDate Fecha límite para cobrarla o pagarla, o null si no tiene.
 * @property paid Céntimos (positivos) que ya se han cobrado o pagado de [total] en pagos parciales.
 */
@Entity(tableName = "debt_table")
data class Debt(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "debtId")
    val debtId: Int = 0,

    @ColumnInfo(name = "debtName")
    val concept: String,

    @ColumnInfo(name = "isActive")
    val isActive: Boolean = true,

    @ColumnInfo(name = "debtCompleted")
    val completed: Date = NOT_COMPLETED,

    @ColumnInfo(name = "debtTotal")
    val total: Long,

    @ColumnInfo(name = "debtDate")
    val date: Date,

    @ColumnInfo(name = "person", defaultValue = "")
    val person: String = "",

    @ColumnInfo(name = "dueDate")
    val dueDate: Date? = null,

    @ColumnInfo(name = "paid", defaultValue = "0")
    val paid: Long = 0,
) {
    /** Lo que queda por cobrar o pagar, con el signo de [total]. */
    val remaining: Long get() = if (total >= 0) total - paid else total + paid

    /** true si está pendiente, tiene fecha límite y ya ha pasado ([now] en milisegundos). */
    fun isOverdue(now: Long): Boolean = isActive && dueDate != null && dueDate.time < now

    companion object {
        /** Valor que usaban las versiones anteriores para las deudas aún sin saldar. */
        val NOT_COMPLETED = Date(1)
    }
}
