// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Plantilla de un movimiento frecuente ("Café 1,50", "Bono bus 20") que se apunta con un toque.
 *
 * @property value Céntimos con el signo del movimiento (negativo para pagos).
 */
@Entity(tableName = "template_table")
data class Template(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val templateId: Int = 0,

    @ColumnInfo(name = "concept")
    val concept: String,

    @ColumnInfo(name = "value")
    val value: Long,

    @ColumnInfo(name = "accountId")
    val accountId: Int,

    @ColumnInfo(name = "categoryId")
    val categoryId: Int? = null,
)
