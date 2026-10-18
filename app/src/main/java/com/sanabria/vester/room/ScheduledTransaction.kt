// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Date

/** Cada cuánto se repite un movimiento programado. */
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Movimiento programado: se apunta solo cuando llega su fecha y, si se repite, vuelve a programarse.
 *
 * Las fechas de cada repetición se calculan siempre desde [startDate] (repetición número [done]) para que,
 * por ejemplo, un pago del día 31 vuelva al 31 después de pasar por febrero.
 *
 * @property value Céntimos, con el signo del movimiento que se apuntará.
 * @property done Veces que ya se ha apuntado.
 * @property nextDate Fecha de la próxima vez (copia calculada de startDate + done repeticiones, para poder buscarla en SQL).
 * @property endDate Última fecha en la que se puede apuntar; null si se repite para siempre.
 */
@Entity(tableName = "scheduled_table", indices = [Index("accountId"), Index("nextDate")])
data class ScheduledTransaction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "accountId")
    val accountId: Int,

    @ColumnInfo(name = "concept")
    val concept: String,

    @ColumnInfo(name = "value")
    val value: Long,

    @ColumnInfo(name = "startDate")
    val startDate: Date,

    @ColumnInfo(name = "repeat")
    val repeat: Repeat,

    @ColumnInfo(name = "done")
    val done: Int = 0,

    @ColumnInfo(name = "nextDate")
    val nextDate: Date = startDate,

    @ColumnInfo(name = "endDate")
    val endDate: Date? = null,

    @ColumnInfo(name = "categoryId")
    val categoryId: Int? = null,
) {
    /** Fecha de la repetición número [occurrence] (la 0 es [startDate]). */
    fun occurrence(occurrence: Int): Date {
        val calendar = Calendar.getInstance().apply { time = startDate }
        when (repeat) {
            Repeat.NONE -> return startDate
            Repeat.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, occurrence)
            Repeat.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, occurrence)
            Repeat.MONTHLY -> calendar.add(Calendar.MONTH, occurrence)
            Repeat.YEARLY -> calendar.add(Calendar.YEAR, occurrence)
        }
        return calendar.time
    }
}
