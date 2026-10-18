// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.GridLayout
import java.text.NumberFormat
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.sanabria.vester.databinding.ItemSheetActionBinding
import com.sanabria.vester.databinding.SheetActionsBinding

/**
 * Una acción de [showActionSheet]: icono sobre un círculo del color [color] y su nombre debajo.
 *
 * @property badge Número que se muestra sobre el icono (por ejemplo, las sugerencias pendientes). 0 no muestra nada.
 */
data class SheetAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @ColorRes val color: Int,
    val badge: Int = 0,
    val onClick: () -> Unit,
)

/** Hoja inferior con una cuadrícula de acciones de colores; al tocar una se cierra y se ejecuta. */
fun Context.showActionSheet(@StringRes title: Int, actions: List<SheetAction>, columns: Int = 3) {
    // Con letra muy grande las etiquetas no caben en tantas columnas
    val shownColumns = if (resources.configuration.fontScale > 1.3f) minOf(columns, 2) else columns
    val dialog = BottomSheetDialog(this)
    val sheet = SheetActionsBinding.inflate(LayoutInflater.from(this))
    sheet.sheetTitle.setText(title)
    sheet.sheetGrid.columnCount = shownColumns
    actions.forEach { action ->
        val item = ItemSheetActionBinding.inflate(LayoutInflater.from(this), sheet.sheetGrid, false)
        val tint = ContextCompat.getColor(this, action.color)
        item.iconBack.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(tint, 0x2E))
        }
        item.icon.setImageResource(action.icon)
        item.icon.imageTintList = ColorStateList.valueOf(tint)
        item.label.setText(action.label)
        if (action.badge > 0) {
            item.badge.text = NumberFormat.getIntegerInstance().format(action.badge)
            item.badge.visibility = android.view.View.VISIBLE
        }
        item.root.setOnClickListener {
            dialog.dismiss()
            action.onClick()
        }
        item.root.layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
        ).apply { width = 0 }
        sheet.sheetGrid.addView(item.root)
    }
    dialog.setContentView(sheet.root)
    // La franja de la barra de navegación toma el color de la hoja en vez de quedarse negra
    dialog.window?.navigationBarColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, 0)
    dialog.show()
}
