// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.sanabria.vester.VesterApplication
import com.sanabria.vester.room.Suggestion
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Lee las notificaciones de las apps de pago, bancos y correo de [NotificationParser.SOURCES] y guarda como
 * sugerencias las que parecen un movimiento. El resto de notificaciones se ignoran sin guardar nada.
 *
 * Solo funciona si el usuario concede el acceso a las notificaciones en los ajustes del sistema.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = NotificationParser.sourceOf(sbn.packageName) ?: return
        val notification = sbn.notification
        // Los resúmenes de grupo repiten lo que ya dicen las notificaciones individuales
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val parsed = NotificationParser.parse(source.kind, title, text) ?: return

        val app = application as VesterApplication
        app.applicationScope.launch {
            app.repository.addSuggestion(
                Suggestion(
                    concept = parsed.concept.ifBlank { source.name },
                    value = parsed.value,
                    date = Date(sbn.postTime),
                    source = source.name,
                    text = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ").take(MAX_TEXT),
                    // "when" no cambia cuando la app actualiza la misma notificación: así no se guarda dos veces
                    notificationKey = "${sbn.packageName}|${notification.`when`}|$title|$text".take(MAX_KEY),
                )
            )
        }
    }

    companion object {
        private const val MAX_TEXT = 400
        private const val MAX_KEY = 500

        /** true si el usuario ya ha dado acceso a las notificaciones. */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun componentName(context: Context) = ComponentName(context, PaymentNotificationListener::class.java)
    }
}
