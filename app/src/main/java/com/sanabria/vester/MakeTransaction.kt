// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sanabria.vester.databinding.ActivityTransactionFormBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.room.Repeat
import com.sanabria.vester.tools.Notifications
import com.sanabria.vester.tools.hapticConfirm
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Nuevo movimiento (pago o ingreso) en una cuenta, o edición de un movimiento programado.
 *
 * - Si la fecha es futura o se elige repetirlo, no se apunta todavía: queda programado y se apunta solo
 *   cuando llega su día (y vuelve a programarse si se repite). La primera vez se pide permiso para avisar cuando
 *   eso ocurra con la app cerrada.
 * - También se usa para saldar deudas y para confirmar sugerencias de notificaciones: en esos casos llega relleno
 *   y la deuda o la sugerencia se resuelven en la misma operación que se apunta el movimiento.
 * - Desde el menú se puede pasar a "Gasto compartido" para dividirlo.
 * - Con [scheduledIntent] edita uno programado: la fecha es la de su próxima vez.
 * Se abre también desde los accesos directos del icono de la app.
 */
class MakeTransaction : BaseActivity() {

    private lateinit var binding: ActivityTransactionFormBinding
    private val dataViewModel: DataViewModel by viewModels()
    private var accounts: List<Account>? = null
    private var categories = emptyList<Category>()
    private var pendingCategoryId: Int? = null
    private lateinit var selectedDate: () -> Long

    private val debtIds by lazy { intent.getIntArrayExtra(EXTRA_DEBT_IDS)?.toList().orEmpty() }
    private val suggestionId by lazy { intent.getIntExtra(EXTRA_SUGGESTION_ID, -1).takeIf { it != -1 } }
    /** Abierto desde "Plantillas": lo que se rellena no se apunta, sino que se guarda como plantilla. */
    private val templateMode by lazy { intent.getBooleanExtra(EXTRA_TEMPLATE, false) }
    private val payDebtId by lazy { intent.getIntExtra(EXTRA_PAY_DEBT_ID, -1).takeIf { it != -1 } }
    private val scheduledId by lazy { intent.getIntExtra(EXTRA_SCHEDULED_ID, -1).takeIf { it != -1 } }

    /** Saldando deudas o confirmando una sugerencia el movimiento es de ahora mismo: ni se repite ni se divide. */
    private val isLinked get() = debtIds.isNotEmpty() || suggestionId != null || payDebtId != null

    private lateinit var extras: TransactionExtras

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        extras = TransactionExtras(this, binding, dataViewModel, savedInstanceState)
        extras.setupSuggestions(::applySuggestion)
        if (scheduledId != null || templateMode) listOf(binding.photoRow, binding.photoCaption).forEach { it.visibility = View.GONE }
        if (templateMode) {
            // Una plantilla no lleva fecha, nota ni repetición
            listOf(binding.dateInput, binding.noteInput).forEach { (it.parent.parent as View).visibility = View.GONE }
            listOf(binding.repeatGroup, binding.makeDebtSwitch).forEach { it.visibility = View.GONE }
        }
        setupToolbar(
            binding.toolbar,
            getString(when { templateMode -> R.string.new_template; scheduledId != null -> R.string.edit_scheduled; else -> R.string.make_transaction }),
        )

        binding.amountInput.setupMoneyInput()
        selectedDate = binding.dateInput.bindDatePicker(intent.getLongExtra(EXTRA_DATE, System.currentTimeMillis()))
        binding.signToggle.setup(R.string.payment, R.string.income, R.color.negative, R.color.positive)
        binding.repeatChips.setItems(resources.getStringArray(R.array.repeat_options).toList())
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_CONCEPT)?.let { binding.conceptInput.setText(it) }
            if (intent.hasExtra(EXTRA_AMOUNT)) {
                val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
                binding.amountInput.setText(formatMoneyInput(amount))
                binding.signToggle.isChecked = amount > 0
            }
        }

        if (isLinked) listOf(binding.repeatGroup, binding.makeDebtSwitch).forEach { it.visibility = View.GONE }
        if (scheduledId != null) binding.makeDebtSwitch.visibility = View.GONE

        dataViewModel.accounts.observe(this) { accounts ->
            if (this.accounts != null) return@observe
            if (accounts.isEmpty()) {
                Toast.makeText(this, R.string.accounts_first, Toast.LENGTH_LONG).show()
                finish()
                return@observe
            }
            this.accounts = accounts
            binding.accountField.setAccounts(accounts, intent.getIntExtra(EXTRA_ACCOUNT_ID, -1))
            scheduledId?.let { loadScheduled(it, accounts, savedInstanceState == null) }
        }

        dataViewModel.categories.observe(this) { list ->
            // Se conserva lo elegido si la lista cambia (o lo que llega de un movimiento programado)
            val selected = binding.categoryField.selectedCategoryId(categories) ?: pendingCategoryId
            categories = list
            binding.categoryField.setCategories(list, selected)
        }

        binding.saveFab.setOnClickListener { save() }
    }

    /** Al elegir un concepto ya usado se propone lo mismo que la última vez: importe, signo, cuenta y categoría. */
    private fun applySuggestion(last: com.sanabria.vester.room.Transaction) {
        if (parseMoney(binding.amountInput.text) == null) {
            binding.amountInput.setText(formatMoneyInput(abs(last.value)))
            binding.signToggle.isChecked = last.value > 0
        }
        categories.indexOfFirst { it.categoryId == last.categoryId }.let { binding.categoryField.setSelection(it + 1) }
        accounts?.indexOfFirst { it.accountId == last.accountId }?.takeIf { it >= 0 }?.let { binding.accountField.setSelection(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extras.saveState(outState)
    }

    /** Rellena el formulario con el movimiento programado que se edita. */
    private fun loadScheduled(id: Int, accounts: List<Account>, fill: Boolean) {
        lifecycleScope.launch {
            val scheduled = dataViewModel.getScheduled(id)
            if (scheduled == null) {
                finish()
                return@launch
            }
            if (fill) {
                binding.conceptInput.setText(scheduled.concept)
                binding.amountInput.setText(formatMoneyInput(abs(scheduled.value)))
                binding.signToggle.isChecked = scheduled.value > 0
                pendingCategoryId = scheduled.categoryId
                binding.categoryField.setCategories(categories, scheduled.categoryId)
                binding.accountField.setAccounts(accounts, scheduled.accountId)
                binding.repeatChips.setItems(resources.getStringArray(R.array.repeat_options).toList(), scheduled.repeat.ordinal)
            }
            selectedDate = binding.dateInput.bindDatePicker(
                scheduled.nextDate.time, futureOnly = scheduled.nextDate.time > System.currentTimeMillis(),
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!isLinked && scheduledId == null && !templateMode) {
            menu.add(Menu.NONE, MENU_SPLIT, Menu.NONE, R.string.split_expense)
            menu.add(Menu.NONE, MENU_TEMPLATE, Menu.NONE, R.string.save_as_template)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_TEMPLATE) {
            saveTemplate(finishAfter = false)
            return true
        }
        if (item.itemId != MENU_SPLIT) return super.onOptionsItemSelected(item)
        // Pasa lo escrito a la pantalla de gasto compartido
        val accountId = accounts?.getOrNull(binding.accountField.selectedItemPosition)?.accountId
        startActivity(
            SplitTransaction.intent(this, parseMoney(binding.amountInput.text), binding.conceptInput.text.toString(), accountId)
        )
        finish()
        return true
    }

    /** Guarda lo escrito como plantilla. Con [finishAfter] cierra la pantalla (modo plantilla); si no, avisa y sigue. */
    private fun saveTemplate(finishAfter: Boolean) {
        val accounts = accounts ?: return
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val value = if (binding.signToggle.isChecked) amount else -amount
        val concept = binding.conceptInput.textOr(getString(R.string.transaction))
        val account = accounts[binding.accountField.selectedItemPosition]
        dataViewModel.addTemplate(concept, value, account.accountId, binding.categoryField.selectedCategoryId(categories))
        Toast.makeText(this, R.string.template_saved, Toast.LENGTH_SHORT).show()
        if (finishAfter) finish()
    }

    private fun save() {
        if (templateMode) {
            saveTemplate(finishAfter = true)
            return
        }
        val accounts = accounts ?: return
        val amount = binding.amountInput.requirePositiveMoney() ?: return
        val value = if (binding.signToggle.isChecked) amount else -amount
        val concept = binding.conceptInput.textOr(getString(R.string.transaction))
        val account = accounts[binding.accountField.selectedItemPosition]
        val date = withCurrentTime(selectedDate())
        val categoryId = binding.categoryField.selectedCategoryId(categories)
        val repeat = if (isLinked) Repeat.NONE else Repeat.entries[binding.repeatChips.selectedIndex]
        binding.saveFab.hapticConfirm()

        // Los movimientos programados no llevan foto: si se programa, la que se haya elegido se descarta
        if (extras.photo.isNotEmpty() && (scheduledId != null || repeat != Repeat.NONE || startOfDay(date.time) > startOfDay(System.currentTimeMillis()))) {
            com.sanabria.vester.tools.Receipts.delete(this, extras.photo)
        }

        scheduledId?.let { id ->
            dataViewModel.updateScheduled(id, account.accountId, value, concept, date, repeat, categoryId)
            finish()
            return
        }
        if (!isLinked && (repeat != Repeat.NONE || startOfDay(date.time) > startOfDay(System.currentTimeMillis()))) {
            dataViewModel.addScheduled(account.accountId, value, concept, date, repeat, end = null, categoryId = categoryId)
            // Si empieza hoy, la primera ya se apunta al guardar
            val startsNow = startOfDay(date.time) <= startOfDay(System.currentTimeMillis())
            Toast.makeText(this, if (startsNow) R.string.scheduled_applied_now else R.string.scheduled_saved, Toast.LENGTH_SHORT).show()
            finishAfterScheduling()
            return
        }
        dataViewModel.addTransaction(
            account.accountId, value, concept, date, debtIds, suggestionId, categoryId, payDebtId, extras.note, extras.photo,
        )
        if (binding.makeDebtSwitch.isChecked) {
            // Si pago algo por otra persona, me lo debe (a cobrar); si me lo adelantan, lo debo (a pagar)
            startActivity(MakeDebt.intent(this, -value, concept))
        }
        finish()
    }

    /** La primera vez que se programa algo se pide permiso para avisar cuando se apunte; después se cierra. */
    private fun finishAfterScheduling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Notifications.shouldAskPermission(this)) {
            Notifications.markAsked(this)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "accountId"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_CONCEPT = "concept"
        private const val EXTRA_DEBT_IDS = "debtIds"
        private const val EXTRA_SUGGESTION_ID = "suggestionId"
        private const val EXTRA_PAY_DEBT_ID = "payDebtId"
        private const val EXTRA_SCHEDULED_ID = "scheduledId"
        private const val EXTRA_DATE = "date"
        private const val MENU_SPLIT = 1
        private const val MENU_TEMPLATE = 2
        private const val EXTRA_TEMPLATE = "template"

        /**
         * @param accountId Cuenta seleccionada al abrir.
         * @param amount Importe propuesto en céntimos; su signo decide si es ingreso o pago.
         * @param debtIds Deudas que se saldan con este movimiento.
         * @param suggestionId Sugerencia de notificación que se confirma con este movimiento.
         * @param payDebtId Deuda a la que se abona este movimiento como pago parcial.
         * @param date Fecha propuesta en milisegundos.
         */
        fun intent(
            context: Context,
            accountId: Int? = null,
            amount: Long? = null,
            concept: String? = null,
            debtIds: List<Int> = emptyList(),
            suggestionId: Int? = null,
            date: Long? = null,
            payDebtId: Int? = null,
        ): Intent = Intent(context, MakeTransaction::class.java).apply {
            accountId?.let { putExtra(EXTRA_ACCOUNT_ID, it) }
            amount?.let { putExtra(EXTRA_AMOUNT, it) }
            concept?.let { putExtra(EXTRA_CONCEPT, it) }
            if (debtIds.isNotEmpty()) putExtra(EXTRA_DEBT_IDS, debtIds.toIntArray())
            suggestionId?.let { putExtra(EXTRA_SUGGESTION_ID, it) }
            payDebtId?.let { putExtra(EXTRA_PAY_DEBT_ID, it) }
            date?.let { putExtra(EXTRA_DATE, it) }
        }

        /** Abre el formulario para crear una plantilla en vez de apuntar un movimiento. */
        fun templateIntent(context: Context): Intent = Intent(context, MakeTransaction::class.java).putExtra(EXTRA_TEMPLATE, true)

        /** Abre el formulario para editar el movimiento programado [scheduledId]. */
        fun scheduledIntent(context: Context, scheduledId: Int): Intent =
            Intent(context, MakeTransaction::class.java).putExtra(EXTRA_SCHEDULED_ID, scheduledId)
    }
}
