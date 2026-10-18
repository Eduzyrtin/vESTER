// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos de la app.
 *
 * Historial de versiones:
 * - 3: cuentas, movimientos y deudas (hasta la 1.6 de Play).
 * - 4: movimientos enlazados (groupId, isTransfer), movimientos programados y sugerencias de notificaciones.
 * - 5: icono (emoji) de las cuentas.
 * - 6: categorías con presupuesto mensual (categoryId en movimientos y programados).
 * - 7: cuentas archivadas; deudas con persona, fecha límite y pagos parciales.
 * - 8: metas de ahorro.
 * - 9: grupos de gastos compartidos (grupos, miembros y gastos).
 * - 10: nota y foto en los movimientos, plantillas y grupos cerrados.
 * - 11: historial de pagos parciales de las deudas.
 *
 * Cualquier cambio en las entidades obliga a subir [version] y añadir una migración; el esquema de cada versión
 * se exporta en app/schemas y la prueba MigrationTest comprueba que se migra sin perder datos.
 * Nunca usar una migración destructiva para versiones a partir de la 3: borraría las cuentas de los usuarios.
 */
@Database(
    entities = [Account::class, Transaction::class, Debt::class, ScheduledTransaction::class, Suggestion::class, Category::class, Goal::class, Template::class, DebtPayment::class,
        ExpenseGroup::class, GroupMember::class, GroupExpense::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun scheduledDao(): ScheduledDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun groupDao(): GroupDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "AppDB"

        /** Versión actual del esquema (subirla obliga a añadir una migración). */
        const val VERSION = 11

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    // Las versiones 1 y 2 solo existieron durante el desarrollo
                    .fallbackToDestructiveMigrationFrom(true, 1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
