// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sanabria.vester.HIDDEN_AMOUNT
import com.sanabria.vester.MakeTransaction
import com.sanabria.vester.OnBoarding
import com.sanabria.vester.R
import com.sanabria.vester.VesterApplication
import com.sanabria.vester.formatMoney
import kotlinx.coroutines.launch

/**
 * Widget de la pantalla de inicio con el saldo total. Al tocarlo se abre la app y el botón + abre un movimiento nuevo.
 * Con el bloqueo de la app activo no enseña el saldo (cualquiera que vea la pantalla de inicio lo vería).
 */
class BalanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context)

    companion object {
        /** Vuelve a dibujar todos los widgets de saldo con los datos de ahora. No hace nada si no hay ninguno colocado. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, BalanceWidget::class.java))
            if (ids.isEmpty()) return
            val app = appContext as VesterApplication
            app.applicationScope.launch {
                val balance = app.repository.totalBalance()
                val shown = balance.currency?.let { formatMoney(balance.cents, it) } ?: formatMoney(balance.cents)
                val views = RemoteViews(appContext.packageName, R.layout.widget_balance)
                views.setTextViewText(R.id.widgetBalance, if (AppSettings.lockEnabled) HIDDEN_AMOUNT else shown)
                views.setOnClickPendingIntent(R.id.widgetRoot, open(appContext, 0, Intent(appContext, OnBoarding::class.java)))
                views.setOnClickPendingIntent(R.id.widgetAdd, open(appContext, 1, Intent(appContext, MakeTransaction::class.java)))
                manager.updateAppWidget(ids, views)
            }
        }

        private fun open(context: Context, code: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
