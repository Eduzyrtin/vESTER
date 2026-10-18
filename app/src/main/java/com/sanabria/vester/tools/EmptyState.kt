// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sanabria.vester.databinding.ViewEmptyBinding

/** Rellena la pantalla vacía de una lista con su [icon], su [title] y, si lo hay, una pista [hint]. */
fun ViewEmptyBinding.bindEmpty(@DrawableRes icon: Int, @StringRes title: Int, @StringRes hint: Int? = null) {
    emptyIcon.setImageResource(icon)
    emptyTitle.setText(title)
    if (hint == null) {
        emptyHint.visibility = View.GONE
    } else {
        emptyHint.setText(hint)
        emptyHint.visibility = View.VISIBLE
    }
}

/** Muestra la pantalla vacía cuando [empty] es true. */
fun ViewEmptyBinding.showWhen(empty: Boolean) {
    root.visibility = if (empty) View.VISIBLE else View.GONE
}
