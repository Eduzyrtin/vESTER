// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cuenta del usuario (banco, efectivo, hucha...).
 *
 * Los nombres de tabla y columna no se pueden cambiar sin una migración: son los de las versiones publicadas.
 *
 * @property total Saldo actual en céntimos.
 * @property color Color ARGB de la tarjeta.
 * @property position Orden en la pantalla principal, empezando en 1.
 * @property icon Emoji de la cuenta (vacío si no tiene).
 * @property archived true si la cuenta está archivada: no sale en la pantalla principal ni en los desplegables ni cuenta en el
 * saldo total, pero conserva sus movimientos y se puede recuperar.
 */
@Entity(tableName = "account_table")
data class Account(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val accountId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "total")
    val total: Long,

    @ColumnInfo(name = "color")
    val color: Int,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "icon", defaultValue = "")
    val icon: String = "",

    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean = false,
)
