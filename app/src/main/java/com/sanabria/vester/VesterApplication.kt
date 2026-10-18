// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sanabria.vester.room.Category
import com.sanabria.vester.datamanager.DataRepository
import com.sanabria.vester.room.AppDatabase
import com.sanabria.vester.tools.AppLock
import com.sanabria.vester.tools.AppSettings
import com.sanabria.vester.tools.BalanceWidget
import com.sanabria.vester.tools.Currencies
import com.sanabria.vester.tools.Notifications
import com.sanabria.vester.tools.Receipts
import com.sanabria.vester.tools.ScheduledWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VesterApplication : Application() {

    /**
     * Ámbito de corrutinas que vive tanto como el proceso. Las escrituras se lanzan aquí y no en el de
     * la pantalla: si se lanzaran en el de la pantalla, cerrarla justo después de guardar podía cancelar la escritura
     * a medias (y descuadrar los saldos).
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: DataRepository by lazy { DataRepository(AppDatabase.getDatabase(this)) }

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Currencies.init(this)
        AppLock.register(this)
        // Los movimientos programados se apuntan al abrir la app y, en segundo plano, cada pocas horas
        ScheduledWorker.enqueue(this)
        seedCategories()
        applicationScope.launch { Receipts.cleanup(this@VesterApplication, repository.usedPhotos()) }
        applicationScope.launch {
            repository.runDueScheduled(System.currentTimeMillis())
            BalanceWidget.refresh(this@VesterApplication)
            Notifications.debtsDue(this@VesterApplication, repository.debtsWithDueDate())
        }
    }

    /** La primera vez que se abre la app con categorías, se crean unas de partida (después el usuario las gestiona). */
    private fun seedCategories() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CATEGORIES_SEEDED, false)) return
        fun category(icon: String, name: Int, color: Int) = Category(name = getString(name), icon = icon, color = ContextCompat.getColor(this, color))
        val defaults = listOf(
            category("🍔", R.string.cat_food, R.color.tile_orange),
            category("🚌", R.string.cat_transport, R.color.tile_blue),
            category("🏠", R.string.cat_home, R.color.tile_indigo),
            category("🎬", R.string.cat_leisure, R.color.tile_purple),
            category("💊", R.string.cat_health, R.color.tile_red),
            category("🛍️", R.string.cat_shopping, R.color.tile_pink),
            category("💡", R.string.cat_bills, R.color.tile_teal),
            category("💼", R.string.cat_salary, R.color.tile_green),
            category("📦", R.string.cat_other, R.color.tile_teal),
        )
        applicationScope.launch {
            repository.seedCategories(defaults)
            prefs.edit { putBoolean(KEY_CATEGORIES_SEEDED, true) }
        }
    }

    private companion object {
        const val PREFS = "vester_state"
        const val KEY_CATEGORIES_SEEDED = "categoriesSeeded"
    }
}
