// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Llama a [onNearEnd] con el número de elementos cargados ([loadedCount], o las filas del adapter si no se da) cuando, al bajar, quedan pocos por ver.
 * Las listas largas se cargan así por tandas en lugar de todas de golpe.
 */
fun RecyclerView.onScrolledNearEnd(
    threshold: Int = 5,
    loadedCount: (() -> Int)? = null,
    onNearEnd: (loaded: Int) -> Unit,
) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val count = recyclerView.adapter?.itemCount ?: return
            if (layoutManager.findLastVisibleItemPosition() >= count - threshold) onNearEnd(loadedCount?.invoke() ?: count)
        }
    })
}
