// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Categoría de los movimientos (comida, transporte...), con un presupuesto mensual opcional.
 *
 * @property icon Emoji de la categoría.
 * @property color Color ARGB con el que se pinta su icono y su barra.
 * @property monthlyBudget Presupuesto de gasto mensual en céntimos; 0 si no tiene.
 * @property position Orden en la lista, empezando en 1.
 */
@Entity(tableName = "category_table")
data class Category(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val categoryId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String,

    @ColumnInfo(name = "color")
    val color: Int,

    @ColumnInfo(name = "monthlyBudget", defaultValue = "0")
    val monthlyBudget: Long = 0,

    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,
) {
    /** Nombre con su emoji delante, para listas y desplegables. */
    val label: String get() = if (icon.isEmpty()) name else "$icon $name"
}
