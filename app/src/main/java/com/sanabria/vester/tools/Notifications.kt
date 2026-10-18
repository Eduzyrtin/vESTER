// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sanabria.vester.Categories
import com.sanabria.vester.DebtsView
import com.sanabria.vester.OnBoarding
import com.sanabria.vester.R
import com.sanabria.vester.HIDDEN_AMOUNT
import com.sanabria.vester.datamanager.BudgetCrossing
import com.sanabria.vester.datamanager.debtName
import com.sanabria.vester.formatMoneyExact
import com.sanabria.vester.room.Debt
import com.sanabria.vester.formatMoney
import com.sanabria.vester.room.Transaction

/** Avisos de la app: movimientos programados apuntados sin tener la app abierta y presupuestos de categorías a punto de agotarse o superados. */
object Notifications {
    private const val CHANNEL_SCHEDULED = "scheduled"
    private const val NOTIFICATION_SCHEDULED = 1
    private const val CHANNEL_BUDGET = "budget"
    private const val NOTIFICATION_BUDGET_BASE = 1000
    private const val CHANNEL_DEBTS = "debts"
    private const val NOTIFICATION_DEBT_BASE = 5000
    private const val KEY_DEBTS_NOTIFIED = "debtsNotified"
    private const val PREFS = "myPrefs"
    private const val KEY_ASKED = "askedNotifications"

    /** true si hay que pedir el permiso de notificaciones (Android 13+) y todavía no se ha pedido. */
    fun shouldAskPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    /** Anota que ya se ha pedido el permiso para no volver a preguntar en cada movimiento programado. */
    fun markAsked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }

    /** Avisa de los [transactions] que acaba de apuntar una tarea en segundo plano (si el usuario permite avisos). */
    fun scheduledApplied(context: Context, transactions: List<Transaction>) {
        if (transactions.isEmpty() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannel(context)
        val first = transactions.first()
        val one = transactions.size == 1
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, OnBoarding::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULED)
            .setSmallIcon(R.drawable.ic_m_clock)
            .setContentTitle(context.getString(if (one) R.string.notif_scheduled_title else R.string.notif_scheduled_many_title))
            .setContentText(
                if (one) "${first.concept} · ${formatMoney(first.value)}" else context.resources.getQuantityString(R.plurals.notif_scheduled_many, transactions.size, transactions.size)
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Ya se ha comprobado que los avisos están permitidos
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_SCHEDULED, notification)
    }

    /** Avisa de que [crossing] ha pasado el 80 % o el 100 % del presupuesto mensual de su categoría. */
    fun budgetAlert(context: Context, crossing: BudgetCrossing) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannel(context)
        val category = crossing.category
        val over = crossing.threshold >= 100
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, Categories::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_m_label)
            .setContentTitle(category.label)
            .setContentText(
                context.getString(
                    if (over) R.string.notif_budget_over else R.string.notif_budget_almost,
                    formatMoney(crossing.spent), formatMoney(category.monthlyBudget),
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BUDGET_BASE + category.categoryId, notification)
    }

    /**
     * Avisa de las deudas con fecha límite que vencen en las próximas 24 horas o que ya han vencido, una sola vez por cada
     * fase de cada deuda (se recuerda en las preferencias qué se ha avisado ya).
     */
    fun debtsDue(context: Context, debts: List<Debt>, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notified = prefs.getStringSet(KEY_DEBTS_NOTIFIED, emptySet()).orEmpty()
        // Las deudas que ya no están pendientes dejan de recordarse
        val activeIds = debts.map { it.debtId.toString() }.toSet()
        val kept = notified.filter { it.substringBefore(':') in activeIds }.toMutableSet()
        val soonLimit = now + 24 * 60 * 60 * 1000L
        val alerts = debts.mapNotNull { debt ->
            val due = debt.dueDate?.time ?: return@mapNotNull null
            val phase = when {
                due < now -> "over"
                due <= soonLimit -> "soon"
                else -> return@mapNotNull null
            }
            val key = "${debt.debtId}:$phase"
            if (key in kept) null else Triple(debt, phase, key)
        }
        if (alerts.isNotEmpty() && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            createChannel(context)
            val open = PendingIntent.getActivity(
                context, 2, Intent(context, DebtsView::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for ((debt, phase, _) in alerts) {
                val amount = if (AppSettings.lockEnabled) HIDDEN_AMOUNT else formatMoneyExact(kotlin.math.abs(debt.remaining))
                val notification = NotificationCompat.Builder(context, CHANNEL_DEBTS)
                    .setSmallIcon(R.drawable.ic_m_receipt)
                    .setContentTitle(context.getString(if (phase == "over") R.string.notif_debt_over_title else R.string.notif_debt_soon_title))
                    .setContentText(
                        context.getString(if (debt.total >= 0) R.string.notif_debt_collect else R.string.notif_debt_pay, debtName(debt), amount)
                    )
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(context).notify(NOTIFICATION_DEBT_BASE + debt.debtId, notification)
            }
        }
        prefs.edit { putStringSet(KEY_DEBTS_NOTIFIED, kept + alerts.map { it.third }) }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCHEDULED, context.getString(R.string.notif_channel_scheduled), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEBTS, context.getString(R.string.notif_channel_debts), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, context.getString(R.string.notif_channel_budget), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
