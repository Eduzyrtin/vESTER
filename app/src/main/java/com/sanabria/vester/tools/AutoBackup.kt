// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanabria.vester.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Copia de seguridad automática semanal de la base de datos en una carpeta que elige el usuario (con el selector de
 * archivos del sistema, así que puede ser del móvil, de una tarjeta o de Drive). Guarda las últimas [KEEP] copias.
 * El archivo es una copia de la base de datos, el mismo tipo que hace "Hacer copia de seguridad" del menú.
 */
object AutoBackup {
    private const val PREFS = "myPrefs"
    private const val KEY_FOLDER = "autoBackupFolder"
    private const val KEY_LAST = "autoBackupLast"
    private const val WORK_NAME = "autoBackup"
    private const val PREFIX = "vester-backup-"
    private const val KEEP = 4

    /** Días sin copia a partir de los cuales se avisa en los ajustes. */
    const val STALE_DAYS = 14L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getString(KEY_FOLDER, null) != null

    /** Cuándo se hizo la última copia (milisegundos), o 0 si todavía no se ha hecho ninguna. */
    fun lastBackup(context: Context): Long = prefs(context).getLong(KEY_LAST, 0L)

    /** true si hay copia automática activada y la última tiene más de [STALE_DAYS] días (o no hay ninguna). */
    fun isStale(context: Context): Boolean =
        isEnabled(context) && System.currentTimeMillis() - lastBackup(context) > TimeUnit.DAYS.toMillis(STALE_DAYS)

    /** Activa la copia semanal en la carpeta [folder], recordando el permiso para escribir en ella. */
    fun enable(context: Context, folder: Uri) {
        context.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs(context).edit { putString(KEY_FOLDER, folder.toString()) }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build(),
        )
    }

    fun disable(context: Context) {
        prefs(context).edit { remove(KEY_FOLDER) }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Hace una copia ahora. Devuelve true si se ha guardado. */
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        val folder = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse) ?: return@withContext false
        runCatching {
            // Pasa a la base de datos lo que aún está en el registro de escritura para que el archivo esté completo
            AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            val source = context.getDatabasePath(AppDatabase.NAME)
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val name = PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".sqlite3"
            val target = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: error("No se pudo crear $name")
            resolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("No se pudo escribir")
            prefs(context).edit { putLong(KEY_LAST, System.currentTimeMillis()) }
            prune(context, folder)
        }.isSuccess
    }

    /** Borra las copias más antiguas para dejar solo las últimas [KEEP]. */
    private fun prune(context: Context, folder: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val backups = mutableListOf<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name.startsWith(PREFIX)) backups += cursor.getString(0) to name
            }
        }
        // El nombre lleva la fecha, así que ordenar por nombre es ordenar por antigüedad
        backups.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }
}

/** Tarea semanal que hace la copia automática. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (!AutoBackup.isEnabled(applicationContext) || AutoBackup.run(applicationContext)) Result.success() else Result.retry()
}
