// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.sanabria.vester.VesterApplication
import com.sanabria.vester.addMonths
import com.sanabria.vester.nextDay
import com.sanabria.vester.nextMonth
import com.sanabria.vester.startOfMonth
import com.sanabria.vester.tools.BalanceWidget
import com.sanabria.vester.tools.Notifications
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.Category
import com.sanabria.vester.room.CategorySpending
import com.sanabria.vester.room.Debt
import com.sanabria.vester.room.ExpenseGroup
import com.sanabria.vester.room.Goal
import com.sanabria.vester.room.GroupExpense
import com.sanabria.vester.room.GroupMember
import com.sanabria.vester.room.PeriodTotals
import com.sanabria.vester.room.Repeat
import com.sanabria.vester.room.ScheduledTransaction
import com.sanabria.vester.room.Suggestion
import com.sanabria.vester.room.Template
import com.sanabria.vester.room.Transaction
import com.sanabria.vester.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cuenta con su último movimiento, para las tarjetas de la pantalla principal. */
data class AccountCard(val account: Account, val lastTransaction: Transaction?)

/** Movimiento junto a la cuenta a la que pertenece (null si la cuenta ya no existe) y su categoría (null si no tiene). */
data class TransactionItem(val transaction: Transaction, val account: Account?, val category: Category? = null)

/** Movimiento programado junto a su cuenta. */
data class ScheduledItem(val scheduled: ScheduledTransaction, val account: Account?)

/**
 * ViewModel compartido por todas las pantallas.
 *
 * Las lecturas son LiveData de Room, que se actualizan solas al cambiar la base de datos, así que las
 * pantallas no necesitan temporizadores ni refrescos manuales. Las escrituras se lanzan en el ámbito de la
 * aplicación ([VesterApplication.applicationScope]) para que terminen aunque la pantalla se cierre.
 */
class DataViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VesterApplication
    private val repository = app.repository

    val accounts: LiveData<List<Account>> = repository.accounts
    val activeDebts: LiveData<List<Debt>> = repository.activeDebts
    val completedDebts: LiveData<List<Debt>> = repository.completedDebts
    val suggestions: LiveData<List<Suggestion>> = repository.suggestions
    val suggestionCount: LiveData<Int> = repository.suggestionCount
    val categories: LiveData<List<Category>> = repository.categories
    val archivedAccounts: LiveData<List<Account>> = repository.archivedAccounts
    val goals: LiveData<List<Goal>> = repository.goals
    val templates: LiveData<List<Template>> = repository.templates
    val groups: LiveData<List<ExpenseGroup>> = repository.groups
    val allGroupMembers: LiveData<List<GroupMember>> = repository.allGroupMembers
    val allGroupExpenses: LiveData<List<GroupExpense>> = repository.allGroupExpenses
    val allAccounts: LiveData<List<Account>> = repository.allAccounts

    /** Ingresos y gastos del mes actual (null si todavía no hay movimientos este mes). */
    val monthTotals: LiveData<PeriodTotals?> = repository.totals(monthly = true, accountId = null).map { totals ->
        val now = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        totals.firstOrNull { it.period == now }
    }

    /** Tarjetas de la pantalla principal. Solo emite cuando ya se conocen cuentas y últimos movimientos. */
    val accountCards: LiveData<List<AccountCard>> =
        combine(repository.accounts, repository.lastTransactionPerAccount) { accounts, last ->
            val lastByAccount = last.associateBy { it.accountId }
            accounts.map { AccountCard(it, lastByAccount[it.accountId]) }
        }

    val scheduled: LiveData<List<ScheduledItem>> = combine(repository.scheduled, repository.accounts) { scheduled, accounts ->
        val byId = accounts.associateBy { it.accountId }
        scheduled.map { ScheduledItem(it, byId[it.accountId]) }
    }

    /* Listas de movimientos: se cargan de PAGE_SIZE en PAGE_SIZE según se llega al final */

    private val allLimit = MutableLiveData(PAGE_SIZE)
    val allTransactions: LiveData<List<TransactionItem>> =
        combine(allLimit.switchMap { repository.allTransactions(it) }, repository.allAccounts, repository.categories, ::withAccounts)

    /** Pide más movimientos de la lista de todas las cuentas si la actual estaba llena. */
    fun loadMoreTransactions(loaded: Int) {
        val limit = allLimit.value ?: PAGE_SIZE
        if (loaded >= limit) allLimit.value = limit + PAGE_SIZE
    }

    /* Pantalla de una cuenta: el id se fija una vez y las consultas cambian solas con él */

    private data class AccountQuery(val id: Int, val limit: Int)

    private val accountQuery = MutableLiveData<AccountQuery>()
    val account: LiveData<Account?> = accountQuery.switchMap { repository.account(it.id) }
    val accountTransactions: LiveData<List<Transaction>> =
        accountQuery.switchMap { repository.transactionsOf(it.id, it.limit) }

    /** Los mismos movimientos con su categoría (la cuenta es la de la pantalla, así que no se repite en cada fila). */
    val accountItems: LiveData<List<TransactionItem>> = combine(accountTransactions, repository.categories) { transactions, categories ->
        val byId = categories.associateBy { it.categoryId }
        transactions.map { TransactionItem(it, null, byId[it.categoryId]) }
    }

    fun selectAccount(id: Int) {
        if (accountQuery.value?.id != id) accountQuery.value = AccountQuery(id, PAGE_SIZE)
    }

    /** Pide más movimientos de la cuenta si la lista actual estaba llena. */
    fun loadMoreAccountTransactions(loaded: Int) {
        val query = accountQuery.value ?: return
        if (loaded >= query.limit) accountQuery.value = query.copy(limit = query.limit + PAGE_SIZE)
    }

    /* Calendario: un único observador que cambia de día, en lugar de uno nuevo por cada día pulsado */

    private val selectedDay = MutableLiveData(startOfDay(System.currentTimeMillis()))
    val dayTransactions: LiveData<List<TransactionItem>> = combine(
        selectedDay.switchMap { repository.transactionsBetween(it, nextDay(it)) },
        repository.allAccounts,
        repository.categories,
        ::withAccounts,
    )
    val dayDebts: LiveData<List<Debt>> = selectedDay.switchMap { repository.debtsBetween(it, nextDay(it)) }

    fun selectDay(timeMillis: Long) {
        selectedDay.value = startOfDay(timeMillis)
    }

    /* Informes */

    private data class ReportQuery(val monthly: Boolean, val accountId: Int?)

    private val reportQuery = MutableLiveData(ReportQuery(monthly = true, accountId = null))
    val reportTotals: LiveData<List<PeriodTotals>> = reportQuery.switchMap { repository.totals(it.monthly, it.accountId) }

    fun setReport(monthly: Boolean, accountId: Int?) {
        val query = ReportQuery(monthly, accountId)
        if (reportQuery.value != query) reportQuery.value = query
    }

    /* Gasto por categoría de un mes (el primer día del mes, en milisegundos) */

    private val categoryMonth = MutableLiveData(startOfMonth(System.currentTimeMillis()))
    val categoryMonthStart: LiveData<Long> = categoryMonth
    val categorySpending: LiveData<List<CategorySpending>> =
        categoryMonth.switchMap { repository.spending(it, nextMonth(it)) }

    /** Cambia el mes que se ve en categorías: -1 el anterior, +1 el siguiente. */
    fun shiftCategoryMonth(months: Int) {
        val current = categoryMonth.value ?: return
        categoryMonth.value = startOfMonth(addMonths(current, months))
    }

    /* Lecturas puntuales */

    suspend fun getAccounts(): List<Account> = repository.getAccounts()
    suspend fun getAccount(id: Int): Account? = repository.getAccount(id)
    suspend fun getTransaction(id: Int): Transaction? = repository.getTransaction(id)
    suspend fun getGroup(groupId: Long): List<Transaction> = repository.getGroup(groupId)
    suspend fun getDebt(id: Int): Debt? = repository.getDebt(id)
    suspend fun getDebtPayments(debtId: Int) = repository.getDebtPayments(debtId)
    suspend fun getSuggestion(id: Int): Suggestion? = repository.getSuggestion(id)
    suspend fun stats(): AppStats = repository.stats()
    suspend fun exportCsv(): String = repository.exportCsv()

    /* Escrituras */

    private fun write(block: suspend DataRepository.() -> Unit): Job =
        app.applicationScope.launch {
            repository.block()
            // El widget de saldo se pone al día con cada cambio
            BalanceWidget.refresh(app)
        }

    fun addAccount(name: String, total: Long, color: Int, currency: String, icon: String = "") =
        write { addAccount(name, total, color, currency, icon) }

    fun updateAccount(id: Int, name: String, total: Long, color: Int, currency: String, icon: String? = null) =
        write { updateAccount(id, name, total, color, currency, icon) }

    fun deleteAccount(id: Int) = write { deleteAccount(id) }

    /**
     * Borra una cuenta guardando antes todo lo que lleva para poder deshacerlo: [onDeleted] recibe esa copia en el hilo
     * principal cuando ya está borrada y se pasa a [restoreAccount] si el usuario se arrepiente.
     */
    fun deleteAccountWithUndo(id: Int, onDeleted: (AccountSnapshot) -> Unit) {
        app.applicationScope.launch {
            val snapshot = repository.snapshotAccount(id) ?: return@launch
            repository.deleteAccount(id)
            withContext(Dispatchers.Main) { onDeleted(snapshot) }
        }
    }

    fun restoreAccount(snapshot: AccountSnapshot) = write { restoreAccount(snapshot) }

    fun setAccountArchived(id: Int, archived: Boolean) = write { setAccountArchived(id, archived) }

    fun reorderAccounts(ids: List<Int>) = write { reorderAccounts(ids) }

    fun addTransaction(
        accountId: Int,
        value: Long,
        concept: String,
        date: Date,
        settleDebtIds: List<Int> = emptyList(),
        suggestionId: Int? = null,
        categoryId: Int? = null,
        payDebtId: Int? = null,
        note: String = "",
        photo: String = "",
    ) = write {
        addTransaction(accountId, value, concept, date, settleDebtIds, suggestionId, categoryId, payDebtId, note, photo)
        // Si el gasto hace pasar la categoría del 80 % o del 100 % de su presupuesto, se avisa
        if (categoryId != null && value < 0) {
            budgetCrossing(categoryId, -value, date)?.let { Notifications.budgetAlert(app, it) }
        }
    }

    fun updateTransaction(id: Int, value: Long, concept: String, date: Date, categoryId: Int? = null, note: String = "", photo: String = "") =
        write { updateTransaction(id, value, concept, date, categoryId, note, photo) }

    fun deleteTransaction(id: Int, revertBalance: Boolean) = write { deleteTransaction(id, revertBalance) }

    fun deleteGroup(groupId: Long, revertBalance: Boolean) = write { deleteGroup(groupId, revertBalance) }

    fun restoreTransaction(transaction: Transaction) = write { restoreTransaction(transaction) }

    fun transfer(fromId: Int, toId: Int, amount: Long, date: Date, conceptFrom: String, conceptTo: String) =
        write { transfer(fromId, toId, amount, date, conceptFrom, conceptTo) }

    fun addSplitExpense(concept: String, date: Date, payments: List<SplitPayment>, shares: List<SplitShare>) =
        write { addSplitExpense(concept, date, payments, shares) }

    fun addScheduled(accountId: Int, value: Long, concept: String, start: Date, repeat: Repeat, end: Date?, categoryId: Int? = null) =
        write { addScheduled(accountId, value, concept, start, repeat, end, categoryId) }

    fun deleteScheduled(id: Int) = write { deleteScheduled(id) }

    suspend fun getScheduled(id: Int): ScheduledTransaction? = repository.getScheduled(id)

    fun updateScheduled(id: Int, accountId: Int, value: Long, concept: String, nextDate: Date, repeat: Repeat, categoryId: Int? = null) =
        write { updateScheduled(id, accountId, value, concept, nextDate, repeat, categoryId) }

    fun deleteSuggestion(id: Int) = write { deleteSuggestion(id) }

    /* Metas de ahorro */

    suspend fun getGoal(id: Int): Goal? = repository.getGoal(id)
    suspend fun accountNetSince(accountId: Int, from: Long): Long = repository.accountNetSince(accountId, from)

    fun addGoal(name: String, icon: String, color: Int, target: Long, accountId: Int?, targetDate: Date?) =
        write { addGoal(name, icon, color, target, accountId, targetDate) }

    fun updateGoal(id: Int, name: String, icon: String, color: Int, target: Long, accountId: Int?, targetDate: Date?) =
        write { updateGoal(id, name, icon, color, target, accountId, targetDate) }

    fun deleteGoal(id: Int) = write { deleteGoal(id) }

    fun addToGoal(id: Int, amount: Long) = write { addToGoal(id, amount) }

    /* Grupos de gastos compartidos */

    fun group(id: Int): LiveData<ExpenseGroup?> = repository.group(id)
    suspend fun groupOnce(id: Int): ExpenseGroup? = repository.getGroup(id)
    fun groupMembers(id: Int): LiveData<List<GroupMember>> = repository.groupMembers(id)
    fun groupExpenses(id: Int): LiveData<List<GroupExpense>> = repository.groupExpenses(id)
    suspend fun getGroupMembers(groupId: Int): List<GroupMember> = repository.getGroupMembers(groupId)
    suspend fun getGroupExpense(id: Int): GroupExpense? = repository.getGroupExpense(id)

    fun addGroup(name: String, icon: String, myName: String, others: List<String>, onCreated: (Int) -> Unit) {
        app.applicationScope.launch {
            val id = repository.addGroup(name, icon, myName, others)
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun renameGroup(id: Int, name: String, icon: String) = write { renameGroup(id, name, icon) }
    fun deleteGroup(id: Int) = write { deleteGroup(id) }
    fun addGroupMember(groupId: Int, name: String) = write { addGroupMember(groupId, name) }

    /** [onResult] recibe false (en el hilo principal) si el miembro no se ha podido quitar por tener gastos. */
    fun removeGroupMember(groupId: Int, memberId: Int, onResult: (Boolean) -> Unit) {
        app.applicationScope.launch {
            val removed = repository.removeGroupMember(groupId, memberId)
            withContext(Dispatchers.Main) { onResult(removed) }
        }
    }

    fun addGroupExpense(
        groupId: Int, payerId: Int, amount: Long, concept: String, date: Date, participants: List<Int>, settlement: Boolean = false,
        parts: Map<Int, Int>? = null,
    ) = write { addGroupExpense(groupId, payerId, amount, concept, date, participants, settlement, parts) }

    fun updateGroupExpense(id: Int, payerId: Int, amount: Long, concept: String, date: Date, participants: List<Int>, parts: Map<Int, Int>? = null) =
        write { updateGroupExpense(id, payerId, amount, concept, date, participants, parts) }

    fun deleteGroupExpense(id: Int) = write { deleteGroupExpense(id) }
    fun restoreGroupExpense(expense: GroupExpense) = write { restoreGroupExpense(expense) }

    /* Plantillas, autocompletar, búsqueda e importación */

    fun addTemplate(concept: String, value: Long, accountId: Int, categoryId: Int?) = write { addTemplate(concept, value, accountId, categoryId) }
    fun deleteTemplate(id: Int) = write { deleteTemplate(id) }
    fun restoreTemplate(template: Template) = write { restoreTemplate(template) }

    /** Apunta la plantilla ahora mismo; [onDone] recibe false (en el hilo principal) si su cuenta ya no existe. */
    fun applyTemplate(id: Int, onDone: (Boolean) -> Unit) {
        app.applicationScope.launch {
            val applied = repository.applyTemplate(id)
            BalanceWidget.refresh(app)
            withContext(Dispatchers.Main) { onDone(applied) }
        }
    }

    suspend fun recentConcepts(): List<String> = repository.recentConcepts()
    suspend fun lastTransactionWithConcept(concept: String): Transaction? = repository.lastTransactionWithConcept(concept)
    suspend fun getTransactionsBetween(from: Long, to: Long): List<Transaction> = repository.getTransactionsBetween(from, to)
    suspend fun everyAccount(): List<Account> = repository.everyAccount()
    suspend fun categoriesOnce(): List<Category> = repository.getCategories()
    suspend fun usedPhotos(): Set<String> = repository.usedPhotos()

    private val searchFilter = MutableLiveData(SearchFilter())
    val searchResults: LiveData<List<TransactionItem>> = combine(
        searchFilter.switchMap { repository.search(it) }, repository.allAccounts, repository.categories, ::withAccounts,
    )

    fun setSearch(filter: SearchFilter) {
        if (searchFilter.value != filter) searchFilter.value = filter
    }

    /** Importa [rows] en la cuenta [accountId]; [onDone] recibe cuántos movimientos nuevos se han añadido. */
    fun importTransactions(accountId: Int, rows: List<ImportedRow>, onDone: (Int) -> Unit) {
        app.applicationScope.launch {
            val count = repository.importTransactions(accountId, rows)
            BalanceWidget.refresh(app)
            withContext(Dispatchers.Main) { onDone(count) }
        }
    }

    /** Cierra el grupo creando las deudas que tocan al usuario; [onDone] recibe cuántas ha creado. */
    fun closeGroup(groupId: Int, onDone: (Int) -> Unit) {
        app.applicationScope.launch {
            val created = repository.closeGroup(groupId)
            withContext(Dispatchers.Main) { onDone(created) }
        }
    }

    fun reopenGroup(groupId: Int) = write { reopenGroup(groupId) }

    /* Categorías */

    suspend fun getCategory(id: Int): Category? = repository.getCategory(id)

    fun addCategory(name: String, icon: String, color: Int, monthlyBudget: Long) =
        write { addCategory(name, icon, color, monthlyBudget) }

    fun updateCategory(id: Int, name: String, icon: String, color: Int, monthlyBudget: Long) =
        write { updateCategory(id, name, icon, color, monthlyBudget) }

    fun deleteCategory(id: Int) = write { deleteCategory(id) }

    /** Crea las categorías de partida si todavía no hay ninguna. */
    fun seedCategories(defaults: List<Category>) = write { seedCategories(defaults) }

    fun addDebt(concept: String, total: Long, date: Date, person: String = "", dueDate: Date? = null) =
        write { addDebt(concept, total, date, person, dueDate) }

    fun updateDebt(id: Int, concept: String, total: Long, date: Date, person: String = "", dueDate: Date? = null) =
        write { updateDebt(id, concept, total, date, person, dueDate) }

    fun deleteDebt(id: Int) = write { deleteDebt(id) }

    fun restoreDebt(debt: Debt) = write { restoreDebt(debt) }

    private fun withAccounts(transactions: List<Transaction>, accounts: List<Account>, categories: List<Category>): List<TransactionItem> {
        val byId = accounts.associateBy { it.accountId }
        val categoryById = categories.associateBy { it.categoryId }
        return transactions.map { TransactionItem(it, byId[it.accountId], categoryById[it.categoryId]) }
    }

    companion object {
        /** Movimientos que se cargan cada vez en las listas. */
        const val PAGE_SIZE = 30
    }
}

/** Combina tres LiveData; solo emite cuando los tres tienen valor. */
fun <A, B, C, R> combine(a: LiveData<A>, b: LiveData<B>, c: LiveData<C>, transform: (A, B, C) -> R): LiveData<R> =
    combine(combine(a, b) { x, y -> x to y }, c) { xy, z -> transform(xy.first, xy.second, z) }

/** Combina dos LiveData; solo emite cuando los dos tienen valor. */
fun <A, B, R> combine(a: LiveData<A>, b: LiveData<B>, transform: (A, B) -> R): LiveData<R> =
    MediatorLiveData<R>().apply {
        var lastA: A? = null
        var lastB: B? = null
        fun update() {
            val valueA = lastA ?: return
            val valueB = lastB ?: return
            value = transform(valueA, valueB)
        }
        addSource(a) { lastA = it; update() }
        addSource(b) { lastB = it; update() }
    }
