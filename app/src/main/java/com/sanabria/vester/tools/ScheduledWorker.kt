// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanabria.vester.VesterApplication
import java.util.concurrent.TimeUnit

/**
 * Tarea periódica que apunta los movimientos programados cuya fecha ha llegado.
 * Además se ejecuta lo mismo cada vez que se abre la app, así que no hace falta que sea muy frecuente.
 */
class ScheduledWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val applied = (applicationContext as VesterApplication).repository.runDueScheduledDetailed(System.currentTimeMillis())
        BalanceWidget.refresh(applicationContext)
        Notifications.scheduledApplied(applicationContext, applied)
        // Y, de paso, los avisos de deudas que vencen mañana o que ya han vencido
        Notifications.debtsDue(applicationContext, (applicationContext as VesterApplication).repository.debtsWithDueDate())
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "scheduledTransactions"

        /** Deja programada la tarea (si ya lo estaba, no hace nada). */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduledWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
