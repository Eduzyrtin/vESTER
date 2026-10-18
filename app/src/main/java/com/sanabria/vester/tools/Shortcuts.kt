// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sanabria.vester.MakeTransaction
import com.sanabria.vester.R
import com.sanabria.vester.room.Account

/**
 * Accesos directos del icono de la app (pulsación larga). Los tres de siempre están en `xml/shortcuts.xml`; aquí se
 * añaden los de "Apuntar en una cuenta" para las primeras cuentas, que abren el movimiento ya con esa cuenta elegida.
 */
object Shortcuts {
    private const val STATIC_SHORTCUTS = 3
    private const val ACCOUNT_SHORTCUTS = 2

    fun update(context: Context, accounts: List<Account>) {
        val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_SHORTCUTS).coerceIn(0, ACCOUNT_SHORTCUTS)
        val shortcuts = accounts.take(room).map { account ->
            ShortcutInfoCompat.Builder(context, "account_${account.accountId}")
                .setShortLabel(account.name)
                .setLongLabel(context.getString(R.string.shortcut_add_to, account.name))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.transaction))
                .setIntent(MakeTransaction.intent(context, accountId = account.accountId).setAction(Intent.ACTION_VIEW))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
