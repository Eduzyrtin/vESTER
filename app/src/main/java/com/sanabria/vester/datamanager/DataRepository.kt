// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.sanabria.vester.room.Account
import com.sanabria.vester.room.AppDatabase
import com.sanabria.vester.room.Category
import com.sanabria.vester.room.CategorySpending
import com.sanabria.vester.room.Debt
import com.sanabria.vester.room.DebtPayment
import com.sanabria.vester.room.ExpenseGroup
import com.sanabria.vester.room.Goal
import com.sanabria.vester.room.GroupExpense
import com.sanabria.vester.room.GroupMember
import com.sanabria.vester.room.PeriodTotals
import com.sanabria.vester.room.Repeat
import com.sanabria.vester.room.ScheduledTransaction
import com.sanabria.vester.room.Suggestion
import com.sanabria.vester.room.Template
import com.sanabria.vester.room.TableStats
import com.sanabria.vester.room.Transaction
import com.sanabria.vester.startOfDay
import com.sanabria.vester.tools.Currencies
import com.sanabria.vester.tools.Total
import java.util.Date
import java.util.UUID
import kotlin.math.abs

/** Parte de un gasto que se paga desde una cuenta ([amount] en céntimos, positivo). */
data class SplitPayment(val accountId: Int, val amount: Long)

/** Cuenta con sus movimientos y programados, tal como estaban antes de borrarla (para deshacer). */
data class AccountSnapshot(val account: Account, val transactions: List<Transaction>, val scheduled: List<ScheduledTransaction>)

/** Presupuesto de una categoría que el gasto del mes acaba de pasar del 80 % o del 100 % ([threshold]). */
data class BudgetCrossing(val category: Category, val spent: Long, val threshold: Int)

/** Parte de un gasto compartido que debe otra persona ([amount] en céntimos, positivo). */
data class SplitShare(val name: String, val amount: Long)

/** Resumen de la base de datos para la pantalla de estadísticas. */
data class AppStats(
    val accounts: TableStats,
    val transactions: TableStats,
    val debts: TableStats,
    val activeDebts: Int,
    val scheduled: TableStats,
    val suggestions: TableStats,
    val totalBalance: Total,
    val firstTransaction: Date?,
)

/**
 * Único punto de acceso a los datos.
 *
 * Toda operación que toca dinero se hace dentro de una transacción de la base de datos, de forma que
 * los movimientos y los saldos de las cuentas se actualizan juntos o no se actualiza nada.
 * Los saldos se modifican sumando la diferencia en SQL (nunca escribiendo un total calculado en pantalla),
 * así que no dependen de que la interfaz tenga datos frescos.
 */
class DataRepository(private val db: AppDatabase) {
    private val accountDao = db.accountDao()
    private val transactionDao = db.transactionDao()
    private val debtDao = db.debtDao()
    private val scheduledDao = db.scheduledDao()
    private val suggestionDao = db.suggestionDao()
    private val categoryDao = db.categoryDao()
    private val goalDao = db.goalDao()
    private val groupDao = db.groupDao()
    private val templateDao = db.templateDao()

    /* Lecturas */

    /** Cuentas en el orden elegido por el usuario. */
    val accounts: LiveData<List<Account>> = accountDao.observeAll()
    /** Todas las cuentas, también las archivadas: sirve para poner nombre a los movimientos. */
    val allAccounts: LiveData<List<Account>> = accountDao.observeEvery()
    val archivedAccounts: LiveData<List<Account>> = accountDao.observeArchived()
    val lastTransactionPerAccount: LiveData<List<Transaction>> = transactionDao.observeLastPerAccount()
    val activeDebts: LiveData<List<Debt>> = debtDao.observeActive()
    val completedDebts: LiveData<List<Debt>> = debtDao.observeCompleted()
    val scheduled: LiveData<List<ScheduledTransaction>> = scheduledDao.observeAll()
    val suggestions: LiveData<List<Suggestion>> = suggestionDao.observeAll()
    val suggestionCount: LiveData<Int> = suggestionDao.observeCount()
    val categories: LiveData<List<Category>> = categoryDao.observeAll()
    val goals: LiveData<List<Goal>> = goalDao.observeAll()
    val templates: LiveData<List<Template>> = templateDao.observeAll()
    val groups: LiveData<List<ExpenseGroup>> = groupDao.observeGroups()
    val allGroupMembers: LiveData<List<GroupMember>> = groupDao.observeAllMembers()
    val allGroupExpenses: LiveData<List<GroupExpense>> = groupDao.observeAllExpenses()

    fun group(id: Int): LiveData<ExpenseGroup?> = groupDao.observeGroup(id)
    fun groupMembers(id: Int): LiveData<List<GroupMember>> = groupDao.observeMembers(id)
    fun groupExpenses(id: Int): LiveData<List<GroupExpense>> = groupDao.observeExpenses(id)

    fun account(id: Int): LiveData<Account?> = accountDao.observe(id)
    fun allTransactions(limit: Int): LiveData<List<Transaction>> = transactionDao.observeAll(limit)
    fun transactionsOf(accountId: Int, limit: Int): LiveData<List<Transaction>> =
        transactionDao.observeByAccount(accountId, limit)
    fun transactionsBetween(from: Long, to: Long): LiveData<List<Transaction>> = transactionDao.observeBetween(from, to)
    fun debtsBetween(from: Long, to: Long): LiveData<List<Debt>> = debtDao.observeBetween(from, to)

    /** Gasto por categoría (null = sin categoría) entre [from] y [to] en milisegundos. */
    fun spending(from: Long, to: Long): LiveData<List<CategorySpending>> = categoryDao.observeSpending(from, to)

    /** Ingresos y gastos por mes ([monthly]) o por año, de una cuenta o de todas ([accountId] null). */
    fun totals(monthly: Boolean, accountId: Int?): LiveData<List<PeriodTotals>> =
        transactionDao.observeTotals(if (monthly) "%Y-%m" else "%Y", accountId)

    suspend fun getAccounts(): List<Account> = accountDao.getAll()

    /** Saldo total de las cuentas visibles (sin archivar), convertido a la moneda principal si hay varias. */
    suspend fun totalBalance(): Total = Currencies.total(accountDao.getAll())
    suspend fun getAccount(id: Int): Account? = accountDao.get(id)
    suspend fun getTransaction(id: Int): Transaction? = transactionDao.get(id)
    suspend fun getTransactionsOf(accountId: Int): List<Transaction> = transactionDao.getByAccount(accountId)
    suspend fun getGroup(groupId: Long): List<Transaction> = transactionDao.getGroup(groupId)
    suspend fun getDebtPayments(debtId: Int): List<DebtPayment> = debtDao.getPayments(debtId)
    suspend fun getDebt(id: Int): Debt? = debtDao.get(id)
    suspend fun getSuggestion(id: Int): Suggestion? = suggestionDao.get(id)

    suspend fun stats(): AppStats = db.withTransaction {
        AppStats(
            accounts = accountDao.stats(),
            transactions = transactionDao.stats(),
            debts = debtDao.stats(),
            activeDebts = debtDao.activeCount(),
            scheduled = scheduledDao.stats(),
            suggestions = suggestionDao.stats(),
            totalBalance = Currencies.total(accountDao.getAll()),
            firstTransaction = transactionDao.firstDate()?.let { Date(it) },
        )
    }

    /* Cuentas */

    /** Crea una cuenta al final de la lista. Devuelve su id. */
    suspend fun addAccount(name: String, total: Long, color: Int, currency: String, icon: String = ""): Int = db.withTransaction {
        val position = accountDao.maxPosition() + 1
        accountDao.insert(Account(name = name, total = total, color = color, currency = currency, position = position, icon = icon)).toInt()
    }

    /** Cambia los datos de una cuenta. [icon] null deja el que tenía. [total] fija el saldo directamente (ajuste manual). */
    suspend fun updateAccount(id: Int, name: String, total: Long, color: Int, currency: String, icon: String? = null) = db.withTransaction {
        val account = accountDao.get(id) ?: return@withTransaction
        accountDao.update(account.copy(name = name, total = total, color = color, currency = currency, icon = icon ?: account.icon))
    }

    /** Borra la cuenta junto con todos sus movimientos (también los programados) y recoloca las demás. */
    suspend fun deleteAccount(id: Int) = db.withTransaction {
        // Las metas enlazadas a esta cuenta conservan lo que llevaban ahorrado, ya sin cuenta
        accountDao.get(id)?.let { goalDao.unlinkAccount(id, it.total.coerceAtLeast(0)) }
        transactionDao.deleteByAccount(id)
        scheduledDao.deleteByAccount(id)
        templateDao.deleteByAccount(id)
        accountDao.delete(id)
        renumber(accountDao.getAll().map { it.accountId })
    }

    /** Archiva una cuenta (deja de verse y de contar en el saldo total) o la recupera. Conserva sus movimientos. */
    suspend fun setAccountArchived(id: Int, archived: Boolean) = db.withTransaction {
        val account = accountDao.get(id) ?: return@withTransaction
        // Al recuperarla vuelve al final de la lista
        val position = if (archived) account.position else accountDao.maxPosition() + 1
        accountDao.update(account.copy(archived = archived, position = position))
        if (archived) renumber(accountDao.getAll().map { it.accountId })
    }

    /** Una cuenta con todo lo que cuelga de ella, para poder deshacer su borrado. */
    suspend fun snapshotAccount(id: Int): AccountSnapshot? = db.withTransaction {
        val account = accountDao.get(id) ?: return@withTransaction null
        AccountSnapshot(account, transactionDao.getByAccount(id), scheduledDao.getByAccount(id))
    }

    /** Vuelve a crear una cuenta borrada con sus movimientos y programados (los saldos ya van dentro de la cuenta). */
    suspend fun restoreAccount(snapshot: AccountSnapshot) = db.withTransaction {
        if (accountDao.countById(snapshot.account.accountId) > 0) return@withTransaction
        accountDao.insert(snapshot.account.copy(position = accountDao.maxPosition() + 1))
        snapshot.transactions.forEach { transactionDao.insert(it) }
        snapshot.scheduled.forEach { scheduledDao.insert(it) }
    }

    /** Guarda el orden de las cuentas: [ids] de la primera a la última. */
    suspend fun reorderAccounts(ids: List<Int>) = db.withTransaction { renumber(ids) }

    private suspend fun renumber(ids: List<Int>) {
        ids.forEachIndexed { index, id -> accountDao.setPosition(id, index + 1) }
    }

    /* Movimientos */

    /**
     * Apunta un movimiento y actualiza el saldo de la cuenta.
     *
     * @param value Céntimos, positivo para ingresos y negativo para pagos.
     * @param settleDebtIds Deudas que quedan saldadas con este movimiento.
     * @param suggestionId Sugerencia de notificación que se confirma con este movimiento (y deja de estar pendiente).
     * @param categoryId Categoría del movimiento, o null si no tiene.
     * @param payDebtId Deuda a la que se abona [value] (valor absoluto) como pago parcial; se salda sola al llegar al total.
     */
    suspend fun addTransaction(
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
    ) = db.withTransaction {
        insertTransaction(
            Transaction(accountId = accountId, concept = concept, value = value, date = date, categoryId = categoryId, note = note, photo = photo)
        )
        if (payDebtId != null) addDebtPayment(payDebtId, abs(value))
        if (settleDebtIds.isNotEmpty()) debtDao.complete(settleDebtIds, System.currentTimeMillis())
        if (suggestionId != null) suggestionDao.delete(suggestionId)
    }

    /** Inserta el movimiento y suma su importe al saldo. Siempre dentro de una transacción. */
    private suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insert(transaction)
        accountDao.addToTotal(transaction.accountId, transaction.value)
    }

    /** Cambia importe, concepto, fecha y categoría de un movimiento y corrige el saldo con la diferencia. */
    suspend fun updateTransaction(
        id: Int, value: Long, concept: String, date: Date, categoryId: Int? = null, note: String = "", photo: String = "",
    ) = db.withTransaction {
        val old = transactionDao.get(id) ?: return@withTransaction
        transactionDao.update(old.copy(value = value, concept = concept, date = date, categoryId = categoryId, note = note, photo = photo))
        accountDao.addToTotal(old.accountId, value - old.value)
    }

    /**
     * Borra un movimiento.
     *
     * @param revertBalance true para devolver su importe al saldo (deshacerlo), false para dejar el saldo como está.
     */
    suspend fun deleteTransaction(id: Int, revertBalance: Boolean) = db.withTransaction {
        val old = transactionDao.get(id) ?: return@withTransaction
        transactionDao.delete(id)
        if (revertBalance) accountDao.addToTotal(old.accountId, -old.value)
    }

    /**
     * Vuelve a poner un movimiento borrado (con su mismo id) y a sumar su importe al saldo.
     * Deshace un borrado hecho con [deleteTransaction] devolviendo el saldo.
     */
    suspend fun restoreTransaction(transaction: Transaction) = db.withTransaction { insertTransaction(transaction) }

    /** Borra todos los movimientos de un grupo (una transferencia entera, por ejemplo) y, si se pide, deshace sus saldos. */
    suspend fun deleteGroup(groupId: Long, revertBalance: Boolean) = db.withTransaction {
        transactionDao.getGroup(groupId).forEach {
            transactionDao.delete(it.transactionId)
            if (revertBalance) accountDao.addToTotal(it.accountId, -it.value)
        }
    }

    /** Pasa [amount] céntimos (positivo) de una cuenta a otra, con un movimiento enlazado en cada una. */
    suspend fun transfer(fromId: Int, toId: Int, amount: Long, date: Date, conceptFrom: String, conceptTo: String) {
        require(fromId != toId) { "Transfer to the same account" }
        db.withTransaction {
            val group = newGroupId()
            insertTransaction(Transaction(accountId = fromId, concept = conceptFrom, value = -amount, date = date, groupId = group, isTransfer = true))
            insertTransaction(Transaction(accountId = toId, concept = conceptTo, value = amount, date = date, groupId = group, isTransfer = true))
        }
    }

    /**
     * Gasto conjunto: se paga desde una o varias cuentas ([payments]) y, si es compartido, cada persona de [shares]
     * queda debiendo su parte (una deuda a cobrar por persona). Los pagos desde varias cuentas quedan enlazados.
     */
    suspend fun addSplitExpense(concept: String, date: Date, payments: List<SplitPayment>, shares: List<SplitShare>) {
        require(payments.isNotEmpty() && payments.all { it.amount > 0 }) { "Invalid payments" }
        db.withTransaction {
            val group = if (payments.size > 1) newGroupId() else null
            payments.forEach {
                insertTransaction(Transaction(accountId = it.accountId, concept = concept, value = -it.amount, date = date, groupId = group))
            }
            shares.filter { it.amount > 0 }.forEach {
                debtDao.insert(Debt(concept = "$concept · ${it.name}", total = it.amount, date = date))
            }
        }
    }

    private fun newGroupId(): Long = UUID.randomUUID().mostSignificantBits

    /* Programados */

    /** Programa un movimiento. Si su fecha ya ha llegado, se apunta en el momento. */
    suspend fun addScheduled(accountId: Int, value: Long, concept: String, start: Date, repeat: Repeat, end: Date?, categoryId: Int? = null) {
        scheduledDao.insert(
            ScheduledTransaction(
                accountId = accountId, concept = concept, value = value, startDate = start, repeat = repeat, endDate = end,
                categoryId = categoryId,
            )
        )
        runDueScheduled(System.currentTimeMillis())
    }

    suspend fun deleteScheduled(id: Int) = scheduledDao.delete(id)

    suspend fun getScheduled(id: Int): ScheduledTransaction? = scheduledDao.get(id)

    /**
     * Edita un movimiento programado. [nextDate] es la próxima vez que se apuntará. Si ni ella ni la repetición cambian,
     * la serie sigue como estaba (con las veces ya apuntadas); si cambian, empieza de nuevo en [nextDate].
     */
    suspend fun updateScheduled(
        id: Int, accountId: Int, value: Long, concept: String, nextDate: Date, repeat: Repeat, categoryId: Int? = null,
    ) = db.withTransaction {
        val old = scheduledDao.get(id) ?: return@withTransaction
        val sameSeries = repeat == old.repeat && startOfDay(nextDate.time) == startOfDay(old.nextDate.time)
        scheduledDao.update(
            if (sameSeries) {
                old.copy(accountId = accountId, value = value, concept = concept, categoryId = categoryId)
            } else {
                old.copy(
                    accountId = accountId, value = value, concept = concept, repeat = repeat, categoryId = categoryId,
                    startDate = nextDate, done = 0, nextDate = nextDate,
                )
            }
        )
    }

    /**
     * Apunta todos los movimientos programados cuya fecha ya ha llegado, incluidas las repeticiones que se hayan
     * perdido mientras el móvil estaba apagado, y programa la siguiente. Devuelve cuántos movimientos ha apuntado.
     */
    suspend fun runDueScheduled(now: Long): Int = runDueScheduledDetailed(now).size

    /** Lo mismo que [runDueScheduled], pero devuelve los movimientos que ha apuntado (para avisar al usuario). */
    suspend fun runDueScheduledDetailed(now: Long): List<Transaction> = db.withTransaction {
        val applied = mutableListOf<Transaction>()
        for (scheduled in scheduledDao.getDue(now)) {
            var done = scheduled.done
            var next = scheduled.occurrence(done)
            val end = scheduled.endDate?.time ?: Long.MAX_VALUE
            // Tope de seguridad para no quedarse en bucle con fechas absurdas
            while (next.time <= now && next.time <= end && applied.size < MAX_SCHEDULED_RUNS) {
                val transaction = Transaction(
                    accountId = scheduled.accountId, concept = scheduled.concept, value = scheduled.value, date = next,
                    categoryId = scheduled.categoryId,
                )
                insertTransaction(transaction)
                applied += transaction
                done++
                if (scheduled.repeat == Repeat.NONE) break
                next = scheduled.occurrence(done)
            }
            val finished = scheduled.repeat == Repeat.NONE && done > scheduled.done || next.time > end
            if (finished) {
                scheduledDao.delete(scheduled.id)
            } else {
                scheduledDao.update(scheduled.copy(done = done, nextDate = next))
            }
        }
        applied
    }


    /** Todos los movimientos como CSV (UTF-8 con BOM para que Excel respete los acentos): fecha, cuenta, concepto, importe, moneda, categoría y si es transferencia. */
    suspend fun exportCsv(): String {
        val accounts = accountDao.getEvery().associateBy { it.accountId }
        val categories = categoryDao.getAll().associateBy { it.categoryId }
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        fun field(text: String): String =
            if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
        return buildString {
            append(0xFEFF.toChar())
            append("Date,Account,Concept,Amount,Currency,Category,Transfer,Note\n")
            transactionDao.getAll().forEach {
                val account = accounts[it.accountId]
                append(format.format(it.date)).append(',')
                append(field(account?.name.orEmpty())).append(',')
                append(field(it.concept)).append(',')
                append(java.math.BigDecimal.valueOf(it.value, 2).toPlainString()).append(',')
                append(field(account?.currency.orEmpty())).append(',')
                append(field(categories[it.categoryId]?.name.orEmpty())).append(',')
                append(if (it.isTransfer) "yes" else "no").append(',')
                append(field(it.note)).append('\n')
            }
        }
    }

    /* Metas de ahorro */

    suspend fun getGoal(id: Int): Goal? = goalDao.get(id)

    suspend fun addGoal(name: String, icon: String, color: Int, target: Long, accountId: Int?, targetDate: Date?): Int =
        goalDao.insert(Goal(name = name, icon = icon, color = color, target = target, accountId = accountId, targetDate = targetDate)).toInt()

    suspend fun updateGoal(
        id: Int, name: String, icon: String, color: Int, target: Long, accountId: Int?, targetDate: Date?,
    ) = db.withTransaction {
        val goal = goalDao.get(id) ?: return@withTransaction
        goalDao.update(goal.copy(name = name, icon = icon, color = color, target = target, accountId = accountId, targetDate = targetDate))
    }

    suspend fun deleteGoal(id: Int) = goalDao.delete(id)

    /** Apunta [amount] céntimos (positivos o negativos) ahorrados a mano en una meta sin cuenta. */
    suspend fun addToGoal(id: Int, amount: Long) = goalDao.addSaved(id, amount)

    /** Cuánto ha cambiado el saldo de una cuenta desde [from] (milisegundos): sirve para estimar el ritmo de ahorro. */
    suspend fun accountNetSince(accountId: Int, from: Long): Long = transactionDao.netSince(accountId, from)

    /* Grupos de gastos compartidos */

    suspend fun getGroup(id: Int): ExpenseGroup? = groupDao.getGroup(id)
    suspend fun getGroupMembers(groupId: Int): List<GroupMember> = groupDao.getMembers(groupId)
    suspend fun getGroupExpense(id: Int): GroupExpense? = groupDao.getExpense(id)

    /** Crea un grupo con el usuario ([myName]) y las demás personas ([others]). Devuelve el id del grupo. */
    suspend fun addGroup(name: String, icon: String, myName: String, others: List<String>): Int = db.withTransaction {
        val id = groupDao.insertGroup(ExpenseGroup(name = name, icon = icon, created = Date())).toInt()
        groupDao.insertMember(GroupMember(groupId = id, name = myName, isMe = true))
        others.map { it.trim() }.filter { it.isNotEmpty() }.forEach { groupDao.insertMember(GroupMember(groupId = id, name = it)) }
        id
    }

    suspend fun renameGroup(id: Int, name: String, icon: String) = db.withTransaction {
        val group = groupDao.getGroup(id) ?: return@withTransaction
        groupDao.updateGroup(group.copy(name = name, icon = icon))
    }

    suspend fun deleteGroup(id: Int) = groupDao.deleteGroup(id)

    suspend fun addGroupMember(groupId: Int, name: String) {
        groupDao.insertMember(GroupMember(groupId = groupId, name = name.trim()))
    }

    /** Quita a un miembro solo si no ha pagado ni participa en ningún gasto. Devuelve false si no se ha podido. */
    suspend fun removeGroupMember(groupId: Int, memberId: Int): Boolean = db.withTransaction {
        val used = groupDao.getExpenses(groupId).any { it.payerId == memberId || memberId in it.participantIds }
        if (used) return@withTransaction false
        groupDao.deleteMember(memberId)
        true
    }

    suspend fun addGroupExpense(
        groupId: Int, payerId: Int, amount: Long, concept: String, date: Date, participants: List<Int>, settlement: Boolean = false,
        parts: Map<Int, Int>? = null,
    ) {
        require(amount > 0 && participants.isNotEmpty()) { "Invalid group expense" }
        groupDao.insertExpense(
            GroupExpense(
                groupId = groupId, payerId = payerId, amount = amount, concept = concept, date = date,
                participants = joinShares(participants, parts), settlement = settlement,
            )
        )
    }

    suspend fun updateGroupExpense(
        id: Int, payerId: Int, amount: Long, concept: String, date: Date, participants: List<Int>, parts: Map<Int, Int>? = null,
    ) =
        db.withTransaction {
            val expense = groupDao.getExpense(id) ?: return@withTransaction
            groupDao.updateExpense(
                expense.copy(payerId = payerId, amount = amount, concept = concept, date = date, participants = joinShares(participants, parts))
            )
        }

    private fun joinShares(participants: List<Int>, parts: Map<Int, Int>?): String =
        if (parts == null) GroupExpense.joinParticipants(participants) else GroupExpense.joinParts(participants.map { it to (parts[it] ?: 1) })

    suspend fun deleteGroupExpense(id: Int) = groupDao.deleteExpense(id)

    /** Vuelve a poner un gasto de grupo borrado (con su mismo id). */
    suspend fun restoreGroupExpense(expense: GroupExpense) {
        groupDao.insertExpense(expense)
    }

    /* Plantillas */

    suspend fun addTemplate(concept: String, value: Long, accountId: Int, categoryId: Int?) {
        templateDao.insert(Template(concept = concept, value = value, accountId = accountId, categoryId = categoryId))
    }

    suspend fun deleteTemplate(id: Int) = templateDao.delete(id)

    suspend fun restoreTemplate(template: Template) {
        templateDao.insert(template)
    }

    /** Apunta ahora mismo el movimiento de una plantilla. Devuelve false si la cuenta ya no existe. */
    suspend fun applyTemplate(id: Int, date: Date = Date()): Boolean = db.withTransaction {
        val template = templateDao.get(id) ?: return@withTransaction false
        if (accountDao.get(template.accountId) == null) return@withTransaction false
        insertTransaction(
            Transaction(
                accountId = template.accountId, concept = template.concept, value = template.value, date = date,
                categoryId = template.categoryId,
            )
        )
        true
    }

    /* Autocompletar y buscar */

    suspend fun recentConcepts(limit: Int = 200): List<String> = transactionDao.recentConcepts(limit)

    suspend fun lastTransactionWithConcept(concept: String): Transaction? = transactionDao.lastByConcept(concept)

    fun search(filter: SearchFilter): LiveData<List<Transaction>> = transactionDao.search(
        filter.text?.takeIf { it.isNotBlank() }, filter.accountId, filter.categoryId, filter.from, filter.to,
        filter.minAbs, filter.maxAbs, filter.kind.ordinal,
    )

    suspend fun getTransactionsBetween(from: Long, to: Long): List<Transaction> = transactionDao.getBetween(from, to)

    suspend fun everyAccount(): List<Account> = accountDao.getEvery()

    suspend fun getCategories(): List<Category> = categoryDao.getAll()

    /** Nombres de las fotos de tickets que usan los movimientos (para borrar las que sobran). */
    suspend fun usedPhotos(): Set<String> = transactionDao.photos().toSet()

    /**
     * Importa [rows] en [accountId] sumando su importe al saldo. Devuelve cuántos ha importado. Los movimientos ya
     * existentes (misma fecha, concepto e importe en esa cuenta) se saltan para poder importar dos veces el mismo archivo.
     */
    suspend fun importTransactions(accountId: Int, rows: List<ImportedRow>): Int = db.withTransaction {
        val existing = transactionDao.getByAccount(accountId).mapTo(HashSet()) { Triple(it.date.time / 60_000, it.concept, it.value) }
        var count = 0
        for (row in rows) {
            val key = Triple(row.date.time / 60_000, row.concept, row.value)
            if (!existing.add(key)) continue
            insertTransaction(
                Transaction(
                    accountId = accountId, concept = row.concept, value = row.value, date = row.date,
                    isTransfer = row.transfer, categoryId = row.categoryId, note = row.note,
                )
            )
            count++
        }
        count
    }

    /**
     * Cierra un grupo y crea una deuda por cada pago pendiente en el que interviene el usuario: a cobrar si le deben y a
     * pagar si debe. Devuelve cuántas deudas ha creado. Un grupo cerrado no admite más gastos (se puede reabrir).
     */
    suspend fun closeGroup(groupId: Int): Int = db.withTransaction {
        val group = groupDao.getGroup(groupId) ?: return@withTransaction 0
        val members = groupDao.getMembers(groupId)
        val me = members.firstOrNull { it.isMe }
        var created = 0
        if (me != null) {
            val names = members.associate { it.memberId to it.name }
            val balances = GroupBalances.balances(groupDao.getExpenses(groupId), members.map { it.memberId })
            for (transfer in GroupBalances.settlements(balances)) {
                val owed = when (me.memberId) {
                    transfer.to -> transfer.amount to names[transfer.from].orEmpty()
                    transfer.from -> -transfer.amount to names[transfer.to].orEmpty()
                    else -> continue
                }
                debtDao.insert(Debt(concept = "${group.name} · ${owed.second}", total = owed.first, date = Date(), person = owed.second))
                created++
            }
        }
        groupDao.updateGroup(group.copy(closed = true))
        created
    }

    suspend fun reopenGroup(groupId: Int) = db.withTransaction {
        val group = groupDao.getGroup(groupId) ?: return@withTransaction
        groupDao.updateGroup(group.copy(closed = false))
    }

    suspend fun debtsWithDueDate(): List<Debt> = debtDao.getActiveWithDueDate()

    /* Categorías */

    suspend fun getCategory(id: Int): Category? = categoryDao.get(id)

    /** Crea una categoría al final de la lista. [monthlyBudget] en céntimos (0 = sin presupuesto). Devuelve su id. */
    suspend fun addCategory(name: String, icon: String, color: Int, monthlyBudget: Long): Int = db.withTransaction {
        val position = categoryDao.maxPosition() + 1
        categoryDao.insert(Category(name = name, icon = icon, color = color, monthlyBudget = monthlyBudget, position = position)).toInt()
    }

    suspend fun updateCategory(id: Int, name: String, icon: String, color: Int, monthlyBudget: Long) = db.withTransaction {
        val category = categoryDao.get(id) ?: return@withTransaction
        categoryDao.update(category.copy(name = name, icon = icon, color = color, monthlyBudget = monthlyBudget))
    }

    /** Borra la categoría; sus movimientos se quedan sin categoría (no se borran). */
    suspend fun deleteCategory(id: Int) = db.withTransaction {
        transactionDao.clearCategory(id)
        scheduledDao.clearCategory(id)
        categoryDao.delete(id)
    }

    /** Crea las categorías de partida si todavía no hay ninguna. Devuelve true si las ha creado. */
    suspend fun seedCategories(defaults: List<Category>): Boolean = db.withTransaction {
        if (categoryDao.count() > 0) return@withTransaction false
        defaults.forEachIndexed { index, category -> categoryDao.insert(category.copy(position = index + 1)) }
        true
    }


    /**
     * Tras apuntar un gasto de [amount] céntimos (positivo) en la categoría [categoryId] con fecha [date], dice si ha
     * hecho pasar el gasto del mes del 80 % o del 100 % de su presupuesto: devuelve la categoría, lo gastado en el mes y
     * el umbral cruzado (80 o 100). null si no hay presupuesto, el gasto no es de este mes o no se cruza ningún umbral.
     */
    suspend fun budgetCrossing(categoryId: Int, amount: Long, date: Date): BudgetCrossing? {
        val category = categoryDao.get(categoryId) ?: return null
        if (category.monthlyBudget <= 0) return null
        val monthStart = com.sanabria.vester.startOfMonth(System.currentTimeMillis())
        if (date.time < monthStart || date.time >= com.sanabria.vester.nextMonth(monthStart)) return null
        val spent = categoryDao.spentIn(categoryId, monthStart, com.sanabria.vester.nextMonth(monthStart))
        val before = spent - amount
        val threshold = listOf(100, 80).firstOrNull { percent ->
            val limit = category.monthlyBudget * percent / 100
            before < limit && spent >= limit
        } ?: return null
        return BudgetCrossing(category, spent, threshold)
    }

    /* Sugerencias de notificaciones */

    /** Guarda una sugerencia. Devuelve false si ya se había guardado esa notificación. */
    suspend fun addSuggestion(suggestion: Suggestion): Boolean = suggestionDao.insert(suggestion) != -1L

    suspend fun deleteSuggestion(id: Int) = suggestionDao.delete(id)

    /* Deudas */

    suspend fun addDebt(concept: String, total: Long, date: Date, person: String = "", dueDate: Date? = null) {
        debtDao.insert(Debt(concept = concept, total = total, date = date, person = person, dueDate = dueDate))
    }

    suspend fun updateDebt(
        id: Int, concept: String, total: Long, date: Date, person: String = "", dueDate: Date? = null,
    ) = db.withTransaction {
        val debt = debtDao.get(id) ?: return@withTransaction
        // Si el nuevo total es menor de lo ya pagado, queda saldada
        val paid = debt.paid.coerceAtMost(abs(total))
        val done = paid > 0 && paid >= abs(total)
        debtDao.update(
            debt.copy(
                concept = concept, total = total, date = date, person = person, dueDate = dueDate, paid = paid,
                isActive = debt.isActive && !done,
                completed = if (debt.isActive && done) Date(System.currentTimeMillis()) else debt.completed,
            )
        )
    }

    suspend fun deleteDebt(id: Int) = debtDao.delete(id)

    /** Vuelve a poner una deuda borrada (con su mismo id y todo lo que llevaba pagado). */
    suspend fun restoreDebt(debt: Debt) {
        debtDao.insert(debt)
    }

    /** Suma [amount] céntimos (positivos) a lo pagado de una deuda; si llega al total, queda saldada. */
    private suspend fun addDebtPayment(id: Int, amount: Long) {
        val debt = debtDao.get(id) ?: return
        debtDao.insertPayment(DebtPayment(debtId = id, amount = amount.coerceAtMost(abs(debt.total) - debt.paid), date = Date()))
        val paid = (debt.paid + amount).coerceAtMost(abs(debt.total))
        val done = paid >= abs(debt.total)
        debtDao.update(
            debt.copy(
                paid = paid,
                isActive = !done,
                completed = if (done) Date(System.currentTimeMillis()) else Debt.NOT_COMPLETED,
            )
        )
    }

    private companion object {
        const val MAX_SCHEDULED_RUNS = 5000
    }
}

/** Tipo de movimiento al buscar. */
enum class SearchKind { ALL, PAYMENTS, INCOME, TRANSFERS }

/** Filtros de la búsqueda de movimientos; los nulos no filtran. Los importes van en céntimos y sin signo. */
data class SearchFilter(
    val text: String? = null,
    val accountId: Int? = null,
    val categoryId: Int? = null,
    val from: Long? = null,
    val to: Long? = null,
    val minAbs: Long? = null,
    val maxAbs: Long? = null,
    val kind: SearchKind = SearchKind.ALL,
)

/** Una fila ya leída de un archivo CSV, lista para importar. */
data class ImportedRow(val date: Date, val concept: String, val value: Long, val categoryId: Int? = null, val note: String = "", val transfer: Boolean = false)
