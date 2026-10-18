// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityImportCsvBinding
import com.sanabria.vester.datamanager.CsvImport
import com.sanabria.vester.datamanager.CsvMapping
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Importa movimientos de un archivo CSV (de otra app, del extracto del banco o el que exporta vESTER). Al abrirse pide
 * el archivo, adivina qué columna es cada dato por su cabecera y deja corregirlo antes de importar. Importar el mismo
 * archivo dos veces no duplica nada.
 */
class ImportCsv : BaseActivity() {

    private lateinit var binding: ActivityImportCsvBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var table = emptyList<List<String>>()
    private var accounts = emptyList<Account>()
    private var categories = emptyList<Category>()

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) finish() else load(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportCsvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.import_csv))
        binding.importFab.hide()
        binding.importFab.setOnClickListener { import() }
        if (savedInstanceState == null) openFile.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream"))
    }

    private fun load(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull() }
            table = text?.let { CsvImport.parse(it) }.orEmpty()
            accounts = dataViewModel.getAccounts()
            categories = dataViewModel.categoriesOnce()
            if (table.size < 2 || accounts.isEmpty()) {
                Toast.makeText(this@ImportCsv, if (accounts.isEmpty()) R.string.accounts_first else R.string.import_empty, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            showMapping()
        }
    }

    private fun showMapping() {
        val header = table.first()
        val guess = CsvImport.guessMapping(header)
        val required = header.map { it.ifBlank { "—" } }
        val optional = listOf(getString(R.string.import_none)) + required
        binding.fileInfo.text = getString(R.string.import_file_info, table.size - 1, header.size)
        binding.accountField.setAccounts(accounts)
        binding.dateColumn.setItems(required)
        binding.amountColumn.setItems(required)
        binding.conceptColumn.setItems(required)
        binding.categoryColumn.setItems(optional)
        binding.noteColumn.setItems(optional)
        binding.dateColumn.setSelection(guess.date.coerceAtLeast(0))
        binding.amountColumn.setSelection(guess.amount.coerceAtLeast(0))
        binding.conceptColumn.setSelection(guess.concept.coerceAtLeast(0))
        binding.categoryColumn.setSelection(guess.category + 1)
        binding.noteColumn.setSelection(guess.note + 1)
        listOf(binding.dateColumn, binding.amountColumn, binding.conceptColumn, binding.categoryColumn, binding.noteColumn)
            .forEach { it.onItemSelected { showPreview() } }
        // La columna de transferencias solo se usa si el archivo la trae (los de vESTER, por ejemplo)
        transferColumn = guess.transfer
        binding.importFab.show()
        showPreview()
    }

    private var transferColumn = -1

    private fun mapping() = CsvMapping(
        date = binding.dateColumn.selectedItemPosition,
        concept = binding.conceptColumn.selectedItemPosition,
        amount = binding.amountColumn.selectedItemPosition,
        category = binding.categoryColumn.selectedItemPosition - 1,
        note = binding.noteColumn.selectedItemPosition - 1,
        transfer = transferColumn,
    )

    private fun result() = CsvImport.rows(table.drop(1), mapping(), categories.associate { it.name to it.categoryId })

    /** Cuántas filas se entienden con las columnas elegidas, para corregirlas antes de importar. */
    private fun showPreview() {
        val result = result()
        binding.preview.text = getString(R.string.import_preview, result.rows.size, result.skipped)
    }

    private fun import() {
        val account = accounts.getOrNull(binding.accountField.selectedItemPosition) ?: return
        val rows = result().rows
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.import_nothing, Toast.LENGTH_LONG).show()
            return
        }
        binding.importFab.hapticConfirm()
        binding.importFab.isEnabled = false
        dataViewModel.importTransactions(account.accountId, rows) { added ->
            Toast.makeText(this, getString(R.string.import_done, added, rows.size - added), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
