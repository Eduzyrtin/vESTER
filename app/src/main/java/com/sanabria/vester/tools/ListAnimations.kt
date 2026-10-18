// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * Animaciones de las listas: las filas que salen se desvanecen, las que cambian de sitio se deslizan y las
 * nuevas aparecen suavemente. Un poco más pausadas que las de serie para que se vea qué ha pasado.
 * Se mantiene la animación de cambio: la necesitan los gestos de deslizar para que la fila vuelva a su sitio.
 */
fun RecyclerView.smoothAnimations() {
    itemAnimator = DefaultItemAnimator().apply {
        addDuration = 220
        removeDuration = 200
        moveDuration = 260
        changeDuration = 160
    }
}
