// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Grupo de gastos compartidos (un viaje, el piso, una cena...): cada miembro apunta lo que paga y la app calcula quién
 * debe a quién. Todo vive en el móvil; no hay servidor.
 */
@Entity(tableName = "group_table")
data class ExpenseGroup(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val groupId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String = "",

    @ColumnInfo(name = "created")
    val created: Date,

    @ColumnInfo(name = "closed", defaultValue = "0")
    val closed: Boolean = false,
) {
    val label: String get() = if (icon.isEmpty()) name else "$icon $name"
}

/**
 * Persona de un grupo.
 *
 * @property isMe true en el miembro que es el propio usuario: sus saldos se cuentan como "tú".
 */
@Entity(
    tableName = "group_member_table",
    foreignKeys = [ForeignKey(entity = ExpenseGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId")],
)
data class GroupMember(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val memberId: Int = 0,

    @ColumnInfo(name = "groupId")
    val groupId: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "isMe", defaultValue = "0")
    val isMe: Boolean = false,
)

/**
 * Gasto de un grupo: lo pagó [payerId] y se reparte a partes iguales entre [participants].
 *
 * Un pago entre miembros para saldar cuentas es un gasto más con [settlement] a true: lo paga quien da el dinero y
 * el único participante es quien lo recibe.
 *
 * @property amount Céntimos, positivos.
 * @property participants Ids de los miembros entre los que se reparte, separados por comas ("3,4,7"). Con reparto por partes
 * cada id lleva sus partes detrás de dos puntos ("3:2,4:1": el 3 paga el doble que el 4).
 */
@Entity(
    tableName = "group_expense_table",
    foreignKeys = [ForeignKey(entity = ExpenseGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId")],
)
data class GroupExpense(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val expenseId: Int = 0,

    @ColumnInfo(name = "groupId")
    val groupId: Int,

    @ColumnInfo(name = "payerId")
    val payerId: Int,

    @ColumnInfo(name = "amount")
    val amount: Long,

    @ColumnInfo(name = "concept")
    val concept: String,

    @ColumnInfo(name = "date")
    val date: Date,

    @ColumnInfo(name = "participants")
    val participants: String,

    @ColumnInfo(name = "settlement", defaultValue = "0")
    val settlement: Boolean = false,
) {
    /** Pares (miembro, partes) en el orden en que se guardaron; sin partes escritas cuenta una. */
    val participantParts: List<Pair<Int, Int>> get() = participants.split(',').mapNotNull { entry ->
        val id = entry.substringBefore(':').trim().toIntOrNull() ?: return@mapNotNull null
        id to (entry.substringAfter(':', "1").trim().toIntOrNull()?.coerceAtLeast(1) ?: 1)
    }

    val participantIds: List<Int> get() = participantParts.map { it.first }

    companion object {
        /** Reparto a partes iguales entre [ids]: "3,4,7". */
        fun joinParticipants(ids: Collection<Int>): String = ids.joinToString(",")

        /** Reparto por partes (miembro a partes): "3:2,4:1". Si todos tienen una parte queda como el reparto igual. */
        fun joinParts(parts: List<Pair<Int, Int>>): String =
            if (parts.all { it.second == 1 }) joinParticipants(parts.map { it.first }) else parts.joinToString(",") { "${it.first}:${it.second}" }
    }
}
