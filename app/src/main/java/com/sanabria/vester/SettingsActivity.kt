// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.sanabria.vester.databinding.ActivitySettingsBinding
import com.sanabria.vester.databinding.ItemExchangeRateBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.AppLock
import com.sanabria.vester.tools.AutoBackup
import com.sanabria.vester.tools.MonthlyReportPdf
import com.sanabria.vester.tools.AppSettings
import com.sanabria.vester.tools.Currencies
import com.sanabria.vester.tools.DynamicTheme
import com.sanabria.vester.tools.MoneyKeypad
import com.sanabria.vester.tools.MoneyWheel
import com.sanabria.vester.tools.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ajustes de la app: tema, colores del sistema, cómo se escriben los importes, bloqueo y modo discreto, día en que
 * empieza el mes de presupuestos y exportar los movimientos a CSV.
 */
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val dataViewModel: DataViewModel by viewModels()

    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val csv = dataViewModel.exportCsv()
            val written = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } }.isSuccess
            }
            Toast.makeText(this@SettingsActivity, if (written) R.string.export_done else R.string.export_error, Toast.LENGTH_LONG).show()
        }
    }

    /** Mes (inicio, en milisegundos) del que se está exportando el informe en PDF. */
    private var pdfMonth = 0L

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val start = pdfMonth
            val end = addMonths(start, 1)
            val transactions = dataViewModel.getTransactionsBetween(start, end)
            val accounts = dataViewModel.everyAccount()
            val categories = dataViewModel.categoriesOnce()
            val title = monthTitle(start)
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { MonthlyReportPdf.write(this@SettingsActivity, it, title, transactions, accounts, categories) }
                }.isSuccess
            }
            Toast.makeText(this@SettingsActivity, if (written) R.string.export_done else R.string.export_error, Toast.LENGTH_LONG).show()
        }
    }

    private val pickBackupFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            showAutoBackup()
            return@registerForActivityResult
        }
        AutoBackup.enable(this, uri)
        // La primera copia se hace al elegir la carpeta, para comprobar que funciona
        lifecycleScope.launch {
            val ok = AutoBackup.run(this@SettingsActivity)
            Toast.makeText(this@SettingsActivity, if (ok) R.string.backup_done else R.string.backup_error_short, Toast.LENGTH_LONG).show()
            showAutoBackup()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.settings))

        setupAppearance()
        setupAmounts()
        setupPrivacy()
        setupMonthStart()
        setupCurrencies()
        binding.exportCsv.setOnClickListener {
            createCsv.launch("vester-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".csv")
        }
        binding.importCsv.setOnClickListener { startActivity(Intent(this, ImportCsv::class.java)) }
        binding.exportPdf.setOnClickListener { askPdfMonth() }
        setupAutoBackup()
        binding.versionLabel.text = getString(R.string.version_label, packageManager.getPackageInfo(packageName, 0).versionName.orEmpty())
    }

    /** Lista de los últimos 12 meses para elegir el del informe en PDF. */
    private fun askPdfMonth() {
        val months = (0 until 12).map { addMonths(calendarMonthStart(System.currentTimeMillis()), -it) }
        AlertDialog.Builder(this)
            .setTitle(R.string.export_pdf)
            .setItems(months.map { monthTitle(it) }.toTypedArray()) { _, which ->
                pdfMonth = months[which]
                createPdf.launch("vester-" + SimpleDateFormat("yyyy-MM", Locale.US).format(Date(pdfMonth)) + ".pdf")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun monthTitle(start: Long): String =
        SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(start)).replaceFirstChar { it.titlecase(Locale.getDefault()) }

    private fun setupAutoBackup() {
        binding.backupNow.setOnClickListener {
            lifecycleScope.launch {
                val ok = AutoBackup.run(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, if (ok) R.string.backup_done else R.string.backup_error_short, Toast.LENGTH_LONG).show()
                showAutoBackup()
            }
        }
        binding.backupFolder.setOnClickListener { pickBackupFolder.launch(null) }
        showAutoBackup()
    }

    private fun showAutoBackup() {
        val enabled = AutoBackup.isEnabled(this)
        binding.autoBackupSwitch.setOnCheckedChangeListener(null)
        binding.autoBackupSwitch.isChecked = enabled
        setupAutoBackupListener()
        binding.autoBackupButtons.visibility = if (enabled) View.VISIBLE else View.GONE
        val last = AutoBackup.lastBackup(this)
        binding.autoBackupStatus.text = when {
            !enabled -> getString(R.string.auto_backup_hint)
            last == 0L -> getString(R.string.auto_backup_never)
            else -> getString(if (AutoBackup.isStale(this)) R.string.auto_backup_old else R.string.auto_backup_last, formatDateTime(Date(last)))
        }
    }

    private fun setupAutoBackupListener() {
        binding.autoBackupSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked == AutoBackup.isEnabled(this)) return@setOnCheckedChangeListener
            if (checked) pickBackupFolder.launch(null) else {
                AutoBackup.disable(this)
                showAutoBackup()
            }
        }
    }

    private fun setupAppearance() {
        binding.themeChips.setItems(
            listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)),
            selected = AppSettings.themeMode.ordinal,
        )
        binding.themeChips.setOnCheckedStateChangeListener { group, _ ->
            val mode = ThemeMode.entries[group.selectedIndex]
            if (mode != AppSettings.themeMode) AppSettings.setThemeMode(mode)
        }
        binding.dynamicColorSwitch.isChecked = DynamicTheme.isEnabled(this)
        if (!DynamicColors.isDynamicColorAvailable()) binding.dynamicColorSwitch.visibility = android.view.View.GONE
        binding.dynamicColorSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != DynamicTheme.isEnabled(this)) {
                DynamicTheme.setEnabled(this, checked)
                recreate()
            }
        }
    }

    /** Teclado del sistema, ruedas o teclado propio: solo uno a la vez. */
    private fun setupAmounts() {
        val selected = when {
            MoneyWheel.isEnabled(this) -> 1
            MoneyKeypad.isEnabled(this) -> 2
            else -> 0
        }
        binding.inputChips.setItems(
            listOf(getString(R.string.input_system), getString(R.string.input_wheels), getString(R.string.input_keypad)),
            selected = selected,
        )
        binding.inputChips.setOnCheckedStateChangeListener { group, _ ->
            val index = group.selectedIndex
            MoneyWheel.setEnabled(this, index == 1)
            MoneyKeypad.setEnabled(this, index == 2)
        }
    }

    private fun setupPrivacy() {
        val available = AppLock.isAvailable(this)
        binding.lockSwitch.isChecked = AppSettings.lockEnabled && available
        binding.lockSwitch.isEnabled = available
        binding.lockHint.visibility = if (available) android.view.View.GONE else android.view.View.VISIBLE
        binding.lockSwitch.setOnCheckedChangeListener { _, checked ->
            AppSettings.setLockEnabled(checked)
            // Se acaba de entrar con la app desbloqueada: no hace falta pedirlo otra vez ahora mismo
            if (checked) AppLock.markUnlocked()
            recreate()
        }
        binding.discreetSwitch.isChecked = AppSettings.discreetMode
        binding.discreetSwitch.setOnCheckedChangeListener { _, checked -> AppSettings.setDiscreetMode(checked) }
    }

    /** Moneda principal y cambio de las demás: solo aparece si las cuentas usan monedas distintas. */
    private fun setupCurrencies() {
        dataViewModel.accounts.observe(this) { accounts ->
            val used = accounts.map { it.currency }.distinct()
            binding.currencySection.visibility = if (used.size > 1) View.VISIBLE else View.GONE
            if (used.size <= 1) return@observe
            val main = Currencies.mainCurrency?.takeIf { it in used } ?: used.first().also { Currencies.setMainCurrency(it) }
            binding.mainCurrencyField.setItems(used)
            binding.mainCurrencyField.setSelection(used.indexOf(main))
            binding.mainCurrencyField.onItemSelected { position ->
                Currencies.setMainCurrency(used[position])
                showRateRows(used, used[position])
            }
            showRateRows(used, main)
        }
    }

    /** Una fila "1 $ = [  ] €" por cada moneda que no es la principal. */
    private fun showRateRows(used: List<String>, main: String) {
        binding.rateRows.removeAllViews()
        for (currency in used.filter { it != main }) {
            val row = ItemExchangeRateBinding.inflate(layoutInflater, binding.rateRows, false)
            row.rateLayout.prefixText = "1 $currency ="
            row.rateLayout.suffixText = main
            row.rateLayout.hint = getString(R.string.exchange_rate)
            Currencies.rate(currency)?.let { row.rateInput.setText(it.toPlainString()) }
            row.rateInput.doAfterTextChanged { text ->
                Currencies.setRate(currency, text?.toString()?.replace(',', '.')?.toBigDecimalOrNull())
            }
            binding.rateRows.addView(row.root)
        }
    }

    private fun setupMonthStart() {
        binding.monthStartField.setItems((1..AppSettings.MAX_MONTH_START_DAY).map { it.toString() })
        binding.monthStartField.setSelection(AppSettings.monthStartDay - 1)
        binding.monthStartField.onItemSelected { position -> AppSettings.setMonthStartDay(position + 1) }
    }
}
