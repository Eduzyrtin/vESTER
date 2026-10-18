// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Fotos de tickets. Viven en la carpeta privada de la app (`files/receipts`), reducidas a [MAX_SIDE] píxeles de lado
 * para que no ocupen mucho; el movimiento solo guarda el nombre del archivo.
 */
object Receipts {
    private const val DIR = "receipts"
    private const val MAX_SIDE = 1600
    private const val QUALITY = 85

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun uri(context: Context, name: String): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file(context, name))

    /** Nombre nuevo para una foto que va a hacer la cámara. */
    fun newName(): String = UUID.randomUUID().toString() + ".jpg"

    /** Copia la imagen de [source] (galería) a la carpeta privada, reducida. Devuelve el nombre o null si no se pudo leer. */
    fun import(context: Context, source: Uri): String? = runCatching {
        val bitmap = context.contentResolver.openInputStream(source)?.use { decode(it.readBytes()) } ?: return null
        val name = newName()
        save(bitmap, file(context, name))
        name
    }.getOrNull()

    /** Reduce una foto que acaba de hacer la cámara (que sale a tamaño completo). */
    fun shrink(context: Context, name: String) {
        runCatching {
            val target = file(context, name)
            val bitmap = decode(target.readBytes()) ?: return
            save(bitmap, target)
        }
    }

    fun delete(context: Context, name: String) {
        if (name.isNotEmpty()) file(context, name).delete()
    }

    /** Borra las fotos que ningún movimiento usa (quedan al borrar movimientos o cambiar de foto). */
    fun cleanup(context: Context, used: Set<String>) {
        // Solo las de más de un día: una foto recién hecha todavía puede estar esperando a que se guarde el movimiento
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir(context).listFiles()?.filter { it.name !in used && it.lastModified() < limit }?.forEach { it.delete() }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun save(bitmap: Bitmap, target: File) {
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
    }
}
