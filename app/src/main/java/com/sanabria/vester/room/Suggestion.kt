// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Posible movimiento detectado en una notificación (Google Wallet, un banco, Gmail...).
 * No toca ningún saldo hasta que el usuario lo confirma.
 *
 * @property value Céntimos: negativo si parece un pago y positivo si parece un ingreso.
 * @property source Nombre de la app que mostró la notificación.
 * @property text Texto original de la notificación, para que el usuario pueda comprobarlo.
 * @property notificationKey Identificador de la notificación, para no guardarla dos veces si se actualiza.
 */
@Entity(tableName = "suggestion_table", indices = [Index(value = ["notificationKey"], unique = true)])
data class Suggestion(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "concept")
    val concept: String,

    @ColumnInfo(name = "value")
    val value: Long,

    @ColumnInfo(name = "date")
    val date: Date,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "notificationKey")
    val notificationKey: String,
)
