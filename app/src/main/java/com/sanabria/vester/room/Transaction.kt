// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Movimiento de una cuenta.
 *
 * @property value Importe en céntimos: positivo para ingresos y negativo para pagos.
 * @property groupId Une los movimientos que forman una sola operación: las dos mitades de una transferencia o
 * las partes de un gasto pagado desde varias cuentas. null si el movimiento va solo (y en los datos anteriores a la 2.0).
   * @property isTransfer true en las dos mitades de una transferencia; los informes no las cuentan como gasto ni ingreso.
 * @property categoryId Categoría del movimiento, o null si no tiene (también en los datos anteriores a la 6).
 * @property note Texto largo aparte del concepto (vacío si no hay).
 * @property photo Nombre del archivo de la foto del ticket en la carpeta privada de la app (vacío si no hay).
 */
@Entity(
    tableName = "transaction_table",
    indices = [Index("accountId"), Index("date"), Index("groupId")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tId")
    val transactionId: Int = 0,

    @ColumnInfo(name = "accountId")
    val accountId: Int,

    @ColumnInfo(name = "concept")
    val concept: String,

    @ColumnInfo(name = "value")
    val value: Long,

    @ColumnInfo(name = "date")
    val date: Date,

    @ColumnInfo(name = "groupId")
    val groupId: Long? = null,

    @ColumnInfo(name = "isTransfer", defaultValue = "0")
    val isTransfer: Boolean = false,

    @ColumnInfo(name = "categoryId")
    val categoryId: Int? = null,

    @ColumnInfo(name = "note", defaultValue = "")
    val note: String = "",

    @ColumnInfo(name = "photo", defaultValue = "")
    val photo: String = "",
)
