// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

/** Eventos de los gestos sobre las tarjetas de cuentas. */
interface ItemTouchHelperAdapter {

    /** La tarjeta en [fromPosition] se está arrastrando sobre [toPosition]. */
    fun onItemMove(fromPosition: Int, toPosition: Int)

    /** Se ha soltado la tarjeta después de arrastrarla: es el momento de guardar el orden. */
    fun onItemDropped()

    /** Se ha deslizado la tarjeta en [position] hacia la derecha: editarla. */
    fun onItemSwipedEdit(position: Int)

    /** Se ha deslizado la tarjeta en [position] hacia la izquierda: borrarla (con confirmación). */
    fun onItemSwipedDelete(position: Int)
}
