// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanabria.vester.room.Repeat
import com.sanabria.vester.room.Suggestion
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Rellena la base de datos REAL de la app con datos de ejemplo para revisar el aspecto de las pantallas a mano.
 * No es una prueba: solo se ejecuta si se pide a propósito (`-e seed true`) y, como todas las instrumentadas,
 * borra lo que hubiera antes en la app instalada.
 *
 *   adb shell am instrument -w -e seed true -e class com.sanabria.vester.SeedDataTest \
 *       com.sanabria.vester.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class SeedDataTest {

    @Test
    fun seed() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Solo con -e seed true", args.getString("seed") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VesterApplication
        val repository = app.repository

        repository.getAccounts().forEach { repository.deleteAccount(it.accountId) }
        com.sanabria.vester.room.AppDatabase.getDatabase(app).openHelper.writableDatabase.execSQL("DELETE FROM debt_table")
        com.sanabria.vester.room.AppDatabase.getDatabase(app).openHelper.writableDatabase.execSQL("DELETE FROM category_table")
        com.sanabria.vester.room.AppDatabase.getDatabase(app).openHelper.writableDatabase.execSQL("DELETE FROM goal_table")
        com.sanabria.vester.room.AppDatabase.getDatabase(app).openHelper.writableDatabase.execSQL("DELETE FROM group_table")
        val food = repository.addCategory("Comida", "🍔", Color.parseColor("#F57C00"), 25_000)
        val home = repository.addCategory("Hogar", "🏠", Color.parseColor("#3949AB"), 70_000)
        val transport = repository.addCategory("Transporte", "🚌", Color.parseColor("#1E88E5"), 4_000)
        val leisure = repository.addCategory("Ocio", "🎬", Color.parseColor("#8E24AA"), 6_000)
        val health = repository.addCategory("Salud", "💊", Color.parseColor("#E53935"), 0)

        fun daysAgo(days: Int, hour: Int = 12) =
            Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()) - (12 - hour) * 3_600_000L)

        val main = repository.addAccount("Cuenta nómina", 184_235, Color.parseColor("#FFE0B2"), "€", "🏦")
        val cash = repository.addAccount("Efectivo", 4_550, Color.parseColor("#C8E6C9"), "€", "💵")
        val saving = repository.addAccount("Hucha viaje a Japón con nombre muy largo", 120_000, Color.parseColor("#BBDEFB"), "€", "🐷")
        val card = repository.addAccount("Tarjeta", -32_075, Color.parseColor("#F8BBD0"), "€")

        repository.addTransaction(main, 145_000, "Nómina octubre", daysAgo(12, 9))
        repository.addTransaction(main, -62_000, "Alquiler", daysAgo(11, 10), categoryId = home)
        repository.addTransaction(main, -5_430, "Mercadona", daysAgo(9, 19), categoryId = food)
        repository.addTransaction(main, -1_299, "Netflix", daysAgo(9, 3), categoryId = leisure)
        repository.addTransaction(main, -3_850, "Gasolina", daysAgo(5, 8), categoryId = transport)
        repository.addTransaction(main, -2_240, "Cena con Ana", daysAgo(2, 22), categoryId = food)
        repository.addTransaction(main, -450, "Café", daysAgo(2, 9), categoryId = food)
        repository.addTransaction(main, -1_890, "Farmacia", daysAgo(1, 17), categoryId = health)
        repository.addTransaction(main, -320, "Café", daysAgo(0, 9), categoryId = food)
        repository.addTransaction(cash, -850, "Bocadillo", daysAgo(3, 14), categoryId = food)
        repository.addTransaction(cash, 2_000, "Me devuelve Pablo", daysAgo(1, 13))
        repository.addTransaction(card, -12_000, "Zapatillas", daysAgo(8, 18))
        repository.addTransaction(saving, 10_000, "Ahorro mensual", daysAgo(10, 9))
        repository.transfer(main, saving, 20_000, daysAgo(4, 11), "Hucha viaje", "Desde nómina")

        repository.addGoal("Viaje a Japón", "✈️", Color.parseColor("#1E88E5"), 400_000, saving, Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(240)))
        repository.addGoal("Móvil nuevo", "📱", Color.parseColor("#43A047"), 60_000, null, null)
        repository.addGoal("Fondo de emergencia", "💰", Color.parseColor("#8E24AA"), 300_000, null, null)

        run {
            val groupId = repository.addGroup("Viaje a Lisboa", "✈️", "Yo", listOf("Ana", "Pablo"))
            val people = repository.getGroupMembers(groupId).map { it.memberId }
            repository.addGroupExpense(groupId, people[0], 24_000, "Hotel", daysAgo(5), people)
            repository.addGroupExpense(groupId, people[1], 7_800, "Cena en el Barrio Alto", daysAgo(4), people)
            repository.addGroupExpense(groupId, people[2], 3_000, "Taxi al aeropuerto", daysAgo(3), listOf(people[0], people[2]))
        }

        repository.addDebt("Cena · Ana", 1_120, daysAgo(2), person = "Ana", dueDate = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(5)))
        repository.addDebt("Entradas concierto · Pablo", 4_500, daysAgo(6), dueDate = daysAgo(1))
        repository.addTransaction(cash, 2_000, "Pablo paga parte", daysAgo(1, 15), payDebtId = com.sanabria.vester.room.AppDatabase.getDatabase(app).debtDao().stats().lastId!!)
        repository.addDebt("Préstamo coche", -25_000, daysAgo(20))
        repository.addDebt("Regalo cumpleaños · Marta", 1_500, daysAgo(14))

        repository.addScheduled(main, -1_299, "Netflix", Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(20)), Repeat.MONTHLY, null)
        repository.addScheduled(main, -62_000, "Alquiler", Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(18)), Repeat.MONTHLY, null)

        repository.addSuggestion(
            Suggestion(
                concept = "Mercadona", value = -2_315, date = Date(), source = "Google Wallet",
                text = "Mercadona 23,15 €", notificationKey = "seed|1",
            )
        )
        Unit
    }
}
