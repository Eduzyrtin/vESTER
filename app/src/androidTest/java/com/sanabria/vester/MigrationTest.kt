// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanabria.vester.datamanager.DataRepository
import com.sanabria.vester.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La base de datos de los usuarios de la 1.x (versión 3) tiene que pasar a la 10 sin perder nada.
 * Usa los esquemas exportados en app/schemas.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate3To4_keepsAccountsTransactionsAndDebts() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position) VALUES (1, 'Banco', 123456, -16744320, '€', 1)")
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position) VALUES (2, 'Efectivo', -2050, -10496, '€', 2)")
            execSQL("INSERT INTO transaction_table (tId, accountId, concept, value, date) VALUES (7, 1, 'Café', -150, 1700000000000)")
            execSQL("INSERT INTO debt_table (debtId, debtName, isActive, debtCompleted, debtTotal, debtDate) VALUES (3, 'Cena', 1, 1, 2550, 1700000000000)")
            close()
        }

        // Comprueba que el esquema resultante es exactamente el que espera Room en la versión 4
        helper.runMigrationsAndValidate(TEST_DB, 4, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        try {
            val repository = DataRepository(db)
            runBlocking {
                val accounts = repository.getAccounts()
                assertEquals(listOf("Banco" to 123456L, "Efectivo" to -2050L), accounts.map { it.name to it.total })
                val transaction = repository.getTransaction(7)!!
                assertEquals(-150L, transaction.value)
                assertNull(transaction.groupId)
                assertFalse(transaction.isTransfer)
                assertTrue(repository.getDebt(3)!!.isActive)
                // Las tablas nuevas existen y están vacías
                val stats = repository.stats()
                assertEquals(0, stats.scheduled.count)
                assertEquals(0, stats.suggestions.count)
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun migrate4To5_addsAnEmptyIconToEveryAccount() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position) VALUES (1, 'Banco', 5000, -16744320, '€', 1)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        try {
            runBlocking {
                val account = DataRepository(db).getAccounts().single()
                assertEquals("Banco" to 5000L, account.name to account.total)
                assertEquals("", account.icon)
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun migrate5To6_addsCategoriesAndLeavesTransactionsUncategorized() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position, icon) VALUES (1, 'Banco', 5000, -16744320, '€', 1, '')")
            execSQL("INSERT INTO transaction_table (tId, accountId, concept, value, date, isTransfer) VALUES (7, 1, 'Café', -150, 1700000000000, 0)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        try {
            runBlocking {
                val repository = DataRepository(db)
                assertNull(repository.getTransaction(7)!!.categoryId)
                assertEquals(5000L, repository.getAccounts().single().total)
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun migrate6To7_keepsDebtsAndAccountsWithNewDefaults() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position, icon) VALUES (1, 'Banco', 5000, -16744320, '€', 1, '')")
            execSQL("INSERT INTO debt_table (debtId, debtName, isActive, debtCompleted, debtTotal, debtDate) VALUES (3, 'Cena · Ana', 1, 1, 2550, 1700000000000)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 7, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        try {
            runBlocking {
                val repository = DataRepository(db)
                assertFalse(repository.getAccounts().single().archived)
                val debt = repository.getDebt(3)!!
                assertEquals("", debt.person)
                assertNull(debt.dueDate)
                assertEquals(0L, debt.paid)
                assertEquals(2550L, debt.remaining)
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun migrate9To10_addsNoteAndPhotoAndKeepsTransactions() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position, icon, archived) VALUES (1, 'Banco', 5000, -16744320, '€', 1, '', 0)")
            execSQL("INSERT INTO transaction_table (tId, accountId, concept, value, date, isTransfer) VALUES (7, 1, 'Café', -150, 1700000000000, 0)")
            execSQL("INSERT INTO group_table (id, name, icon, created) VALUES (1, 'Viaje', '', 1700000000000)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 10, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        try {
            runBlocking {
                val repository = DataRepository(db)
                val transaction = repository.getTransaction(7)!!
                assertEquals("", transaction.note)
                assertEquals("", transaction.photo)
                assertEquals(-150L, transaction.value)
                assertFalse(repository.getGroup(1)!!.closed)
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun migrate3To11_chainsAllMigrations() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO account_table (id, name, total, color, currency, position) VALUES (1, 'Banco', 777, -1, '$', 1)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 11, true).close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
