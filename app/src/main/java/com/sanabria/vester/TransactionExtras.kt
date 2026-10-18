// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityTransactionFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Transaction
import com.sanabria.vester.tools.Receipts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo opcional del formulario de movimiento que comparten "Nuevo" y "Modificar": la nota, la foto del ticket y (solo si
 * se pide) las sugerencias de concepto. Hay que crearlo en `onCreate`, porque registra los selectores de foto.
 */
class TransactionExtras(
    private val activity: BaseActivity,
    private val binding: ActivityTransactionFormBinding,
    private val dataViewModel: DataViewModel,
    savedInstanceState: Bundle?,
) {
    /** Nombre de la foto del ticket (vacío si no hay). */
    var photo: String = savedInstanceState?.getString(STATE_PHOTO).orEmpty()
        private set
    private var pendingCameraName: String? = savedInstanceState?.getString(STATE_PENDING)

    /** La foto con la que se abrió el formulario: si al guardar es otra, la antigua deja de usarse. */
    var originalPhoto: String = ""

    val note: String get() = binding.noteInput.text.toString().trim()

    private val takePicture = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { done ->
        val name = pendingCameraName ?: return@registerForActivityResult
        pendingCameraName = null
        if (done) {
            Receipts.shrink(activity, name)
            setPhoto(name)
        } else {
            Receipts.delete(activity, name)
        }
    }

    private val pickPicture = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        activity.lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { Receipts.import(activity, uri) }
            if (name == null) Toast.makeText(activity, R.string.receipt_error, Toast.LENGTH_LONG).show() else setPhoto(name)
        }
    }

    init {
        binding.photoCamera.setOnClickListener {
            val name = Receipts.newName()
            pendingCameraName = name
            takePicture.launch(Receipts.uri(activity, name))
        }
        binding.photoGallery.setOnClickListener {
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.photoRemove.setOnClickListener { setPhoto("") }
        binding.photoThumb.setOnClickListener {
            if (photo.isEmpty()) return@setOnClickListener
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Receipts.uri(activity, photo), "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { activity.startActivity(view) }
        }
        showPhoto()
    }

    /** Rellena la nota y la foto con las de un movimiento que se edita. */
    fun fill(transaction: Transaction, savedInstanceState: Bundle?) {
        originalPhoto = transaction.photo
        if (savedInstanceState == null) {
            binding.noteInput.setText(transaction.note)
            photo = transaction.photo
            showPhoto()
        }
    }

    fun saveState(outState: Bundle) {
        outState.putString(STATE_PHOTO, photo)
        outState.putString(STATE_PENDING, pendingCameraName)
    }

    private fun setPhoto(name: String) {
        // Una foto nueva que se sustituye antes de guardar ya no hace falta
        if (photo.isNotEmpty() && photo != originalPhoto && photo != name) Receipts.delete(activity, photo)
        photo = name
        showPhoto()
    }

    private fun showPhoto() {
        val has = photo.isNotEmpty() && Receipts.file(activity, photo).exists()
        binding.photoThumb.visibility = if (has) View.VISIBLE else View.GONE
        binding.photoRemove.visibility = if (has) View.VISIBLE else View.GONE
        if (has) binding.photoThumb.setImageURI(Receipts.uri(activity, photo)) else binding.photoThumb.setImageDrawable(null)
    }

    /**
     * Sugiere los conceptos ya usados mientras se escribe. Al elegir uno se llama a [onPick] con el último movimiento
     * que lo usó, para que el formulario proponga el mismo importe, cuenta y categoría.
     */
    fun setupSuggestions(onPick: (Transaction) -> Unit) {
        activity.lifecycleScope.launch {
            val concepts = dataViewModel.recentConcepts()
            binding.conceptInput.setAdapter(ArrayAdapter(activity, R.layout.item_spinner_dropdown, concepts))
        }
        binding.conceptInput.setOnItemClickListener { parent, _, position, _ ->
            val concept = parent.getItemAtPosition(position) as String
            activity.lifecycleScope.launch { dataViewModel.lastTransactionWithConcept(concept)?.let(onPick) }
        }
    }

    private companion object {
        const val STATE_PHOTO = "receiptPhoto"
        const val STATE_PENDING = "receiptPending"
    }
}
