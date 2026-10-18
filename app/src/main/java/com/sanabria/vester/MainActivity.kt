// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.sanabria.vester.databinding.ActivityMainBinding
import com.sanabria.vester.datamanager.AccountCard
import com.sanabria.vester.datamanager.AdapterAccount
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.AppDatabase
import com.sanabria.vester.room.PeriodTotals
import com.sanabria.vester.tools.AppSettings
import com.sanabria.vester.tools.Currencies
import com.sanabria.vester.tools.ItemTouchHelperAdapter
import com.sanabria.vester.tools.Shortcuts
import com.sanabria.vester.tools.MainRecyclerItemTouchHelper
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.SheetAction
import com.sanabria.vester.tools.Tutorial
import com.sanabria.vester.tools.TutorialStep
import com.sanabria.vester.tools.showActionSheet
import de.raphaelebner.roomdatabasebackup.core.RoomBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Pantalla principal: resumen del saldo, tarjetas de las cuentas, botón "Nuevo" y barra inferior.
 *
 * Las tarjetas se ordenan manteniéndolas pulsadas y arrastrándolas, y al deslizarlas a un lado aparecen
 * las opciones de la cuenta. En tablets se muestran en dos columnas.
 * "Nuevo" abre una hoja con lo que se puede apuntar y "Más" otra con el resto de pantallas.
 */
class MainActivity : BaseActivity(), ItemTouchHelperAdapter {

    private lateinit var binding: ActivityMainBinding
    private val dataViewModel: DataViewModel by viewModels()
    private lateinit var adapter: AdapterAccount
    private lateinit var backup: RoomBackup
    private var tutorialPending = true
    private var pendingSuggestions = 0
    private var summaryShown = false
    /** Si los importes estaban ocultos al dibujar la pantalla: al cambiar el modo discreto en ajustes se dibuja de nuevo. */
    private var amountsWereHidden = AppSettings.amountsHidden
    private var summaryCurrency: String? = null
    private var monthTotals: PeriodTotals? = null
    /** El detalle de la cuenta elegida, en el panel derecho (solo en tablet). */
    private var detail: AccountDetail? = null
    private var detailSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, "")
        setNavigationBarColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer))

        // Tiene que crearse antes de que la actividad arranque: registra los selectores de archivo
        backup = RoomBackup(this)

        adapter = AdapterAccount(::openAccount, ::showAccountMenu)
        binding.detailPane?.let { pane ->
            detail = AccountDetail(this, pane, dataViewModel, onDeleted = {
                detailSelected = false
                showDetailPane(false)
            })
            binding.detailEmpty?.bindEmpty(R.drawable.ic_m_wallet, R.string.select_account)
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.account_columns))
        binding.recyclerView.smoothAnimations()
        binding.recyclerView.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)
        ItemTouchHelper(MainRecyclerItemTouchHelper(this)).attachToRecyclerView(binding.recyclerView)

        dataViewModel.archivedAccounts.observe(this) { invalidateOptionsMenu() }
        dataViewModel.accountCards.observe(this) { cards ->
            Shortcuts.update(this, cards.map { it.account })
            adapter.submit(cards)
            showSummary(cards)
            // En tablet se abre la primera cuenta si todavía no hay ninguna elegida
            if (detail != null && !detailSelected && cards.isNotEmpty()) {
                detailSelected = true
                detail?.select(cards.first().account.accountId)
                showDetailPane(true)
            } else if (cards.isEmpty()) {
                detailSelected = false
                showDetailPane(false)
            }
            if (tutorialPending) {
                tutorialPending = false
                binding.recyclerView.post { showTutorial(cards.size) }
            }
        }
        dataViewModel.monthTotals.observe(this) {
            monthTotals = it
            showMonth()
        }

        dataViewModel.suggestionCount.observe(this) { pendingSuggestions = it }

        listOf(binding.combination, binding.all, binding.debts, binding.more).forEach { item ->
            item.setOnClickListener { onBottomAction(item.id) }
        }
        binding.newFab.setOnClickListener { showNewSheet() }
        binding.emptyAction.setOnClickListener { startActivity(Intent(this, AddAccount::class.java)) }
    }

    /** Muestra el saldo total y el resumen del mes, o la pantalla vacía si todavía no hay cuentas. */
    private fun showSummary(cards: List<AccountCard>) {
        val hasAccounts = cards.isNotEmpty()
        binding.summaryCard.visibility = if (hasAccounts) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (hasAccounts) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasAccounts) View.GONE else View.VISIBLE
        binding.newFab.visibility = if (hasAccounts) View.VISIBLE else View.GONE
        if (!hasAccounts) return
        // Con monedas distintas se suma sin símbolo, como en la combinación de cuentas
        summaryCurrency = cards.map { it.account.currency }.distinct().singleOrNull()
        val total = Currencies.total(cards.map { it.account })
        binding.totalValue.setText(total.currency?.let { formatMoney(total.cents, it) } ?: formatMoney(total.cents), animate = summaryShown)
        summaryShown = true
        showMonth()
    }

    private fun showMonth() {
        val month = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date())
        binding.monthLabel.text = getString(R.string.this_month_value, month)
        binding.monthIncome.text = getString(R.string.up_value, money(monthTotals?.income ?: 0))
        binding.monthExpense.text = getString(R.string.down_value, money(abs(monthTotals?.expense ?: 0)))
    }

    private fun money(cents: Long): String = summaryCurrency?.let { formatMoney(cents, it) } ?: formatMoney(cents)

    override fun onResume() {
        super.onResume()
        if (AppSettings.amountsHidden != amountsWereHidden) {
            recreate()
            return
        }
        invalidateOptionsMenu()
        // Al volver de la ayuda se repiten los tutoriales
        if (getPreferences(MODE_PRIVATE).getBoolean(PREF_REPEAT_TUTORIAL, false)) {
            getPreferences(MODE_PRIVATE).edit { putBoolean(PREF_REPEAT_TUTORIAL, false) }
            binding.recyclerView.post { showTutorial(adapter.itemCount) }
        }
    }

    private fun onBottomAction(itemId: Int) {
        when (itemId) {
            R.id.combination -> open(AccountCombination::class.java, accountsNeeded = 1)
            R.id.all -> open(AllTransactions::class.java, accountsNeeded = 1)
            R.id.debts -> open(DebtsView::class.java)
            R.id.more -> showMoreSheet()
        }
    }

    /** Abre [target] si hay al menos [accountsNeeded] cuentas; si no, avisa de que hay que crearlas. */
    private fun open(target: Class<*>, accountsNeeded: Int = 0) {
        if (adapter.itemCount < accountsNeeded) {
            Toast.makeText(this, R.string.accounts_first, Toast.LENGTH_LONG).show()
        } else {
            startActivity(Intent(this, target))
        }
    }

    /** Hoja del botón "Nuevo": lo que se puede apuntar. Todo menos la deuda necesita alguna cuenta. */
    private fun showNewSheet() {
        showActionSheet(
            R.string.new_menu,
            listOf(
                SheetAction(R.drawable.ic_m_add, R.string.make_transaction, R.color.tile_orange) {
                    open(MakeTransaction::class.java, accountsNeeded = 1)
                },
                SheetAction(R.drawable.ic_m_swap, R.string.transfer, R.color.tile_blue) {
                    open(TransferAccount::class.java, accountsNeeded = 2)
                },
                SheetAction(R.drawable.ic_m_receipt, R.string.make_debt, R.color.tile_red) { open(MakeDebt::class.java) },
                SheetAction(R.drawable.ic_m_split, R.string.split_expense, R.color.tile_green) {
                    open(SplitTransaction::class.java, accountsNeeded = 1)
                },
            ),
            columns = 4,
        )
    }

    /** Hoja de la barra inferior "Más": las pantallas que no están en la barra. */
    private fun showMoreSheet() {
        showActionSheet(
            R.string.more,
            listOf(
                SheetAction(R.drawable.ic_m_search, R.string.search, R.color.tile_indigo) { open(Search::class.java, accountsNeeded = 1) },
                SheetAction(R.drawable.ic_m_calendar, R.string.calendar, R.color.tile_blue) { open(Calendar::class.java) },
                SheetAction(R.drawable.ic_m_clock, R.string.scheduled, R.color.tile_purple) { open(ScheduledTransactions::class.java) },
                SheetAction(R.drawable.ic_m_coins, R.string.cash_counter, R.color.tile_orange) {
                    open(CoinCounter::class.java, accountsNeeded = 1)
                },
                SheetAction(R.drawable.ic_m_bell, R.string.suggestions, R.color.tile_pink, badge = pendingSuggestions) {
                    open(Suggestions::class.java)
                },
                SheetAction(R.drawable.ic_m_label, R.string.categories, R.color.tile_green) { open(Categories::class.java) },
                SheetAction(R.drawable.ic_m_flag, R.string.goals, R.color.tile_pink) { open(Goals::class.java) },
                SheetAction(R.drawable.ic_m_bolt, R.string.templates, R.color.tile_orange) { open(Templates::class.java, accountsNeeded = 1) },
                SheetAction(R.drawable.ic_m_group, R.string.groups, R.color.tile_teal) { open(Groups::class.java) },
                SheetAction(R.drawable.ic_m_pie, R.string.reports, R.color.tile_teal) { open(Reports::class.java) },
                SheetAction(R.drawable.ic_m_stats, R.string.stats, R.color.tile_indigo) { open(Stats::class.java) },
            ),
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.add).setIcon(R.drawable.ic_baseline_add_24)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.archivedAccounts).isVisible = !dataViewModel.archivedAccounts.value.isNullOrEmpty()
        menu.findItem(R.id.toggleAmounts).apply {
            isVisible = AppSettings.discreetMode
            setIcon(if (AppSettings.revealed) R.drawable.ic_m_visibility_off else R.drawable.ic_m_visibility)
            setTitle(if (AppSettings.revealed) R.string.hide_amounts else R.string.show_amounts)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.add -> {
            startActivity(Intent(this, AddAccount::class.java))
            true
        }
        R.id.toggleAmounts -> {
            AppSettings.revealed = !AppSettings.revealed
            // Se recrea para que todos los importes de la pantalla se dibujen de nuevo
            recreate()
            true
        }
        R.id.archivedAccounts -> {
            showArchivedAccounts()
            true
        }
        R.id.settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.help -> {
            Tutorial.reset(this)
            getPreferences(MODE_PRIVATE).edit { putBoolean(PREF_REPEAT_TUTORIAL, true) }
            startActivity(OnBoarding.helpIntent(this))
            true
        }
        R.id.backup -> {
            runBackup(restore = false)
            true
        }
        R.id.restore -> {
            runBackup(restore = true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Copia de seguridad o restauración de la base de datos en un archivo que elige el usuario.
     * Se usa el selector de archivos del sistema, así que no hace falta ningún permiso de almacenamiento.
     * La librería cierra la base de datos, por eso al terminar bien se reinicia la app.
     */
    private fun runBackup(restore: Boolean) {
        backup
            .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
            .database(AppDatabase.getDatabase(this))
            .enableLogDebug(BuildConfig.DEBUG)
            .backupIsEncrypted(false)
            .apply {
                onCompleteListener { success, message, exitCode ->
                    Log.d(TAG, "backup restore=$restore success=$success exitCode=$exitCode message=$message")
                    val text = when {
                        success && restore -> getString(R.string.restore_done)
                        success -> getString(R.string.backup_done)
                        else -> getString(R.string.backup_error, message)
                    }
                    Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
                    if (success) restartApp(Intent(this@MainActivity, MainActivity::class.java))
                }
            }
        if (restore) backup.restore() else backup.backup()
    }

    /* Gestos sobre las tarjetas */

    override fun onItemMove(fromPosition: Int, toPosition: Int) = adapter.moveItem(fromPosition, toPosition)

    override fun onItemDropped() {
        dataViewModel.reorderAccounts(adapter.accountOrder)
    }

    override fun onItemSwipedEdit(position: Int) = editAccount(adapter.getAccount(position))

    override fun onItemSwipedDelete(position: Int) = confirmDelete(adapter.getAccount(position))

    private fun editAccount(account: Account) = startActivity(EditAccount.intent(this, account.accountId))

    /** Menú ⋮ de la tarjeta: lo mismo que los gestos, para quien no los descubra. */
    private fun showAccountMenu(account: Account, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_EDIT, 0, R.string.start_edit)
            menu.add(0, MENU_ARCHIVE, 1, R.string.archive_account)
            menu.add(0, MENU_DELETE, 2, R.string.delete_account)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    MENU_EDIT -> editAccount(account)
                    MENU_ARCHIVE -> archiveAccount(account)
                    else -> confirmDelete(account)
                }
                true
            }
            show()
        }
    }

    /** Borrar una cuenta borra también todos sus movimientos, así que se explica y se pide confirmación. */
    private fun confirmDelete(account: Account) {
        AlertDialog.Builder(this)
            .setTitle(account.name)
            .setMessage("${getString(R.string.delete_account_sure)}\n${getString(R.string.deleting_account)}")
            .setPositiveButton(R.string.yes) { _, _ ->
                // Se borra al momento y, durante unos segundos, se puede deshacer con todo lo que llevaba
                dataViewModel.deleteAccountWithUndo(account.accountId) { snapshot ->
                    Snackbar.make(binding.root, getString(R.string.account_deleted, account.name), Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { dataViewModel.restoreAccount(snapshot) }
                        .show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /** Archivar quita la cuenta de la lista y del saldo total sin borrar nada; se recupera desde el menú. */
    private fun archiveAccount(account: Account) {
        dataViewModel.setAccountArchived(account.accountId, true)
        Snackbar.make(binding.root, getString(R.string.account_archived, account.name), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { dataViewModel.setAccountArchived(account.accountId, false) }
            .show()
    }

    /** Lista de cuentas archivadas: al elegir una, vuelve a la pantalla principal. */
    private fun showArchivedAccounts() {
        val archived = dataViewModel.archivedAccounts.value.orEmpty()
        if (archived.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.archived_accounts)
            .setItems(archived.map { it.name }.toTypedArray()) { _, which ->
                dataViewModel.setAccountArchived(archived[which].accountId, false)
                Snackbar.make(binding.root, getString(R.string.account_restored, archived[which].name), Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * En tablet el detalle se ve en el panel derecho; en móvil se abre su pantalla con una transición en la que la
     * tarjeta [card] se "abre" hasta ocupar la pantalla.
     */
    private fun openAccount(account: Account, card: View) {
        val detail = detail
        if (detail != null) {
            detail.select(account.accountId)
            showDetailPane(true)
            return
        }
        card.transitionName = AccountTransaction.TRANSITION_NAME
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, card, AccountTransaction.TRANSITION_NAME)
        startActivity(AccountTransaction.intent(this, account.accountId), options.toBundle())
    }

    /** Muestra el detalle de la cuenta elegida o, si no hay ninguna, el aviso de que se elija una. */
    private fun showDetailPane(selected: Boolean) {
        binding.detailPane?.root?.visibility = if (selected) View.VISIBLE else View.GONE
        binding.detailEmpty?.showWhen(!selected && adapter.itemCount > 0)
    }

    /* Tutoriales (cada uno se muestra una sola vez) */

    private fun showTutorial(accounts: Int) {
        if (isFinishing) return
        when {
            accounts == 0 -> mainTutorial()
            accounts >= 2 -> reorderTutorial()
        }
    }

    private fun mainTutorial() {
        Tutorial.show(
            this, "main",
            listOf(
                TutorialStep(findViewById(R.id.add), R.string.create_account),
                TutorialStep(binding.newFab, R.string.make_transaction_tut),
                TutorialStep(binding.all, R.string.all_tut),
                TutorialStep(binding.combination, R.string.combination_tut),
                TutorialStep(binding.more, R.string.tools_tut),
            ),
        )
    }

    private fun reorderTutorial() {
        val card = binding.recyclerView.layoutManager?.findViewByPosition(1) ?: return
        Tutorial.show(this, "reorder", listOf(TutorialStep(card.findViewById(R.id.cardView) ?: card, R.string.reorder_tut)))
    }

    private companion object {
        const val TAG = "MainActivity"
        const val MENU_EDIT = 1
        const val MENU_ARCHIVE = 3
        const val MENU_DELETE = 2
        const val PREF_REPEAT_TUTORIAL = "repeatTutorial"
    }
}
