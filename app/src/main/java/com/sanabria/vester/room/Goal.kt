// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Meta de ahorro (un viaje, un móvil, un fondo de emergencia...).
 *
 * El progreso sale de una cuenta ([accountId], por ejemplo una hucha) o, si no tiene cuenta, de lo que se ha ido
 * apuntando a mano en [saved].
 *
 * @property target Importe a conseguir, en céntimos.
 * @property saved Céntimos ahorrados a mano (solo se usa si la meta no está enlazada a una cuenta).
 * @property targetDate Fecha en la que se quiere tenerla, o null si no hay prisa.
 */
@Entity(tableName = "goal_table")
data class Goal(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val goalId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String,

    @ColumnInfo(name = "color")
    val color: Int,

    @ColumnInfo(name = "target")
    val target: Long,

    @ColumnInfo(name = "saved", defaultValue = "0")
    val saved: Long = 0,

    @ColumnInfo(name = "accountId")
    val accountId: Int? = null,

    @ColumnInfo(name = "targetDate")
    val targetDate: Date? = null,
) {
    val label: String get() = if (icon.isEmpty()) name else "$icon $name"
}
