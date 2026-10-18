// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanabria.vester.datamanager.DataRepository
import com.sanabria.vester.datamanager.GroupBalances
import com.sanabria.vester.datamanager.ImportedRow
import com.sanabria.vester.datamanager.SearchFilter
import com.sanabria.vester.datamanager.SearchKind
import com.sanabria.vester.datamanager.SplitPayment
import com.sanabria.vester.datamanager.SplitShare
import com.sanabria.vester.room.AppDatabase
import com.sanabria.vester.room.PeriodTotals
import com.sanabria.vester.room.Repeat
import com.sanabria.vester.room.ScheduledTransaction
import com.sanabria.vester.room.Suggestion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprueba que los saldos cuadran con cada operación, contra una base de datos Room real en memoria.
 */
@RunWith(AndroidJUnit4::class)
class DataRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DataRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = DataRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun total(id: Int) = repository.getAccount(id)!!.total

    @Test
    fun transactionsUpdateTheBalance() = runBlocking {
        val id = repository.addAccount("Cash", 1000, 0, "€")
        repository.addTransaction(id, -29, "Coffee", Date())
        repository.addTransaction(id, 115, "Tip", Date())
        assertEquals(1000 - 29 + 115L, total(id))
        assertEquals(2, repository.getTransactionsOf(id).size)
    }

    @Test
    fun restoringADeletedTransactionBringsBackTheTransactionAndTheBalance() = runBlocking {
        val id = repository.addAccount("Cash", 1000, 0, "€")
        repository.addTransaction(id, -300, "Lunch", Date())
        val transaction = repository.getTransactionsOf(id).single()
        repository.deleteTransaction(transaction.transactionId, revertBalance = true)
        assertEquals(1000L, total(id))
        assertTrue(repository.getTransactionsOf(id).isEmpty())
        repository.restoreTransaction(transaction)
        assertEquals(700L, total(id))
        assertEquals(transaction.transactionId, repository.getTransactionsOf(id).single().transactionId)
    }

    @Test
    fun editingAScheduledTransactionKeepsTheSeriesIfNothingMoves() = runBlocking {
        val id = repository.addAccount("Cash", 0, 0, "€")
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 5) }.time
        repository.addScheduled(id, -1000, "Gym", start, Repeat.MONTHLY, null)
        val scheduled = db.scheduledDao().getDue(Long.MAX_VALUE).single()

        repository.updateScheduled(scheduled.id, id, -1500, "Gym plus", scheduled.nextDate, Repeat.MONTHLY)
        val edited = repository.getScheduled(scheduled.id)!!
        assertEquals(-1500L, edited.value)
        assertEquals("Gym plus", edited.concept)
        assertEquals(scheduled.startDate, edited.startDate)

        val later = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 9) }.time
        repository.updateScheduled(scheduled.id, id, -1500, "Gym plus", later, Repeat.WEEKLY)
        val restarted = repository.getScheduled(scheduled.id)!!
        assertEquals(Repeat.WEEKLY, restarted.repeat)
        assertEquals(later, restarted.startDate)
        assertEquals(0, restarted.done)
        assertEquals(0L, total(id))
    }

    @Test
    fun accountIconIsStoredAndKeptWhenNotGiven() = runBlocking {
        val id = repository.addAccount("Cash", 0, 0, "€", icon = "🐷")
        assertEquals("🐷", repository.getAccount(id)!!.icon)
        repository.updateAccount(id, "Cash", 0, 0, "€")
        assertEquals("🐷", repository.getAccount(id)!!.icon)
        repository.updateAccount(id, "Cash", 0, 0, "€", icon = "")
        assertEquals("", repository.getAccount(id)!!.icon)
    }

    @Test
    fun editingATransactionAppliesOnlyTheDifference() = runBlocking {
        val id = repository.addAccount("Cash", 0, 0, "€")
        repository.addTransaction(id, -500, "Food", Date())
        val transaction = repository.getTransactionsOf(id).single()
        repository.updateTransaction(transaction.transactionId, -750, "Food & drinks", transaction.date)
        assertEquals(-750L, total(id))
        repository.updateTransaction(transaction.transactionId, 200, "Refund", transaction.date)
        assertEquals(200L, total(id))
    }

    @Test
    fun deletingATransactionCanRevertTheBalanceOrNot() = runBlocking {
        val id = repository.addAccount("Cash", 0, 0, "€")
        repository.addTransaction(id, -300, "A", Date())
        repository.addTransaction(id, -200, "B", Date())
        val (b, a) = repository.getTransactionsOf(id).let { list -> list.first { it.concept == "B" } to list.first { it.concept == "A" } }
        repository.deleteTransaction(a.transactionId, revertBalance = true)
        assertEquals(-200L, total(id))
        repository.deleteTransaction(b.transactionId, revertBalance = false)
        assertEquals(-200L, total(id))
        assertTrue(repository.getTransactionsOf(id).isEmpty())
    }

    @Test
    fun transferMovesMoneyBetweenAccounts() = runBlocking {
        val from = repository.addAccount("Bank", 10_000, 0, "€")
        val to = repository.addAccount("Savings", 0, 0, "€")
        repository.transfer(from, to, 2_550, Date(), "To Savings", "From Bank")
        assertEquals(7_450L, total(from))
        assertEquals(2_550L, total(to))
        assertEquals(-2_550L, repository.getTransactionsOf(from).single().value)
        assertEquals(2_550L, repository.getTransactionsOf(to).single().value)
    }

    @Test
    fun deletingAnAccountRemovesItsTransactionsAndKeepsOrder() = runBlocking {
        val a = repository.addAccount("A", 0, 0, "€")
        val b = repository.addAccount("B", 0, 0, "€")
        val c = repository.addAccount("C", 0, 0, "€")
        repository.addTransaction(b, 100, "x", Date())
        repository.addTransaction(c, 100, "y", Date())
        repository.deleteAccount(b)
        assertTrue(repository.getTransactionsOf(b).isEmpty())
        assertEquals(1, repository.getTransactionsOf(c).size)
        assertEquals(listOf(a to 1, c to 2), repository.getAccounts().map { it.accountId to it.position })
    }

    @Test
    fun reorderSavesPositions() = runBlocking {
        val a = repository.addAccount("A", 0, 0, "€")
        val b = repository.addAccount("B", 0, 0, "€")
        val c = repository.addAccount("C", 0, 0, "€")
        repository.reorderAccounts(listOf(c, a, b))
        assertEquals(listOf(c, a, b), repository.getAccounts().map { it.accountId })
    }

    @Test
    fun settlingDebtsMarksThemCompletedWithTheTransaction() = runBlocking {
        val id = repository.addAccount("Cash", 0, 0, "€")
        repository.addDebt("Tickets", 5_000, Date())
        repository.addDebt("Dinner", -1_500, Date())
        val debts = db.debtDao().let { dao -> listOf(dao.get(1)!!, dao.get(2)!!) }
        repository.addTransaction(id, debts.sumOf { it.total }, "Tickets / Dinner", Date(), debts.map { it.debtId })
        assertEquals(3_500L, total(id))
        debts.forEach { assertFalse(repository.getDebt(it.debtId)!!.isActive) }
    }

    @Test
    fun deletingAWholeTransferRevertsBothAccounts() = runBlocking {
        val from = repository.addAccount("Bank", 10_000, 0, "€")
        val to = repository.addAccount("Savings", 0, 0, "€")
        repository.transfer(from, to, 2_500, Date(), "To Savings", "From Bank")
        val leg = repository.getTransactionsOf(from).single()
        assertTrue(leg.isTransfer)
        assertEquals(2, repository.getGroup(leg.groupId!!).size)
        repository.deleteGroup(leg.groupId!!, revertBalance = true)
        assertEquals(10_000L, total(from))
        assertEquals(0L, total(to))
        assertTrue(repository.getTransactionsOf(to).isEmpty())
    }

    @Test
    fun splitExpenseFromSeveralAccountsCreatesDebtsForTheOthers() = runBlocking {
        val bank = repository.addAccount("Bank", 10_000, 0, "€")
        val cash = repository.addAccount("Cash", 5_000, 0, "€")
        repository.addSplitExpense(
            "Dinner", Date(),
            payments = listOf(SplitPayment(bank, 6_000), SplitPayment(cash, 3_000)),
            shares = listOf(SplitShare("Ana", 3_000), SplitShare("Luis", 3_000)),
        )
        assertEquals(4_000L, total(bank))
        assertEquals(2_000L, total(cash))
        val bankPart = repository.getTransactionsOf(bank).single()
        assertEquals(2, repository.getGroup(bankPart.groupId!!).size)
        assertFalse(bankPart.isTransfer)
        val debts = listOf(repository.getDebt(1)!!, repository.getDebt(2)!!)
        assertEquals(listOf("Dinner · Ana" to 3_000L, "Dinner · Luis" to 3_000L), debts.map { it.concept to it.total })
    }

    @Test
    fun monthlyScheduledCatchesUpMissedMonthsAndKeepsTheDay() = runBlocking {
        val id = repository.addAccount("Bank", 0, 0, "€")
        // Del 31 de enero: 31 ene, 28 feb (no hay 31), 31 mar, 30 abr; la siguiente, 31 de mayo
        val start = date(2026, Calendar.JANUARY, 31)
        db.scheduledDao().insert(
            ScheduledTransaction(accountId = id, concept = "Rent", value = -50_000, startDate = start, repeat = Repeat.MONTHLY)
        )
        val applied = repository.runDueScheduled(date(2026, Calendar.MAY, 15).time)
        assertEquals(4, applied)
        assertEquals(-200_000L, total(id))
        val days = repository.getTransactionsOf(id).map { Calendar.getInstance().apply { time = it.date }.get(Calendar.DAY_OF_MONTH) }
        assertEquals(listOf(30, 31, 28, 31), days)
        val next = db.scheduledDao().getDue(Long.MAX_VALUE).single()
        assertEquals(date(2026, Calendar.MAY, 31), next.nextDate)
        // Volver a ejecutarlo el mismo día no apunta nada más
        assertEquals(0, repository.runDueScheduled(date(2026, Calendar.MAY, 15).time))
    }

    @Test
    fun oneOffScheduledIsAppliedOnceAndRemoved() = runBlocking {
        val id = repository.addAccount("Bank", 0, 0, "€")
        db.scheduledDao().insert(
            ScheduledTransaction(accountId = id, concept = "Gift", value = 2_000, startDate = date(2026, Calendar.MARCH, 1), repeat = Repeat.NONE)
        )
        assertEquals(0, repository.runDueScheduled(date(2026, Calendar.FEBRUARY, 1).time))
        assertEquals(1, repository.runDueScheduled(date(2026, Calendar.MARCH, 2).time))
        assertEquals(2_000L, total(id))
        assertTrue(db.scheduledDao().getDue(Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun confirmingASuggestionAddsTheTransactionAndRemovesIt() = runBlocking {
        val id = repository.addAccount("Bank", 1_000, 0, "€")
        assertTrue(repository.addSuggestion(Suggestion(concept = "Mercadona", value = -1_234, date = Date(), source = "Google Wallet", text = "", notificationKey = "k1")))
        // La misma notificación no se guarda dos veces
        assertFalse(repository.addSuggestion(Suggestion(concept = "Mercadona", value = -1_234, date = Date(), source = "Google Wallet", text = "", notificationKey = "k1")))
        val suggestion = db.suggestionDao().get(1)!!
        repository.addTransaction(id, suggestion.value, suggestion.concept, suggestion.date, suggestionId = suggestion.id)
        assertEquals(-234L, total(id))
        assertNull(db.suggestionDao().get(1))
    }

    @Test
    fun reportsSumIncomeAndExpensesButNotTransfers() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val cash = repository.addAccount("Cash", 0, 0, "€")
        repository.addTransaction(bank, 10_000, "Salary", date(2026, Calendar.MARCH, 1))
        repository.addTransaction(bank, -2_500, "Food", date(2026, Calendar.MARCH, 5))
        repository.addTransaction(cash, -500, "Coffee", date(2026, Calendar.APRIL, 2))
        repository.transfer(bank, cash, 3_000, date(2026, Calendar.MARCH, 10), "To Cash", "From Bank")
        val months = repository.totals(monthly = true, accountId = null).awaitValue()
        assertEquals(
            listOf(PeriodTotals("2026-04", 0, -500), PeriodTotals("2026-03", 10_000, -2_500)),
            months,
        )
        val bankYears = repository.totals(monthly = false, accountId = bank).awaitValue()
        assertEquals(listOf(PeriodTotals("2026", 10_000, -2_500)), bankYears)
    }

    @Test
    fun categorySpendingGroupsExpensesAndIgnoresTransfersAndIncome() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val cash = repository.addAccount("Cash", 0, 0, "€")
        val food = repository.addCategory("Food", "🍔", 1, 20_000)
        repository.addTransaction(bank, -2_500, "Lunch", date(2026, Calendar.MARCH, 5), categoryId = food)
        repository.addTransaction(cash, -500, "Coffee", date(2026, Calendar.MARCH, 6), categoryId = food)
        repository.addTransaction(bank, -700, "Misc", date(2026, Calendar.MARCH, 7))
        repository.addTransaction(bank, 9_000, "Salary", date(2026, Calendar.MARCH, 1), categoryId = food)
        repository.transfer(bank, cash, 1_000, date(2026, Calendar.MARCH, 8), "To", "From")
        repository.addTransaction(bank, -400, "April", date(2026, Calendar.APRIL, 1), categoryId = food)
        val march = repository.spending(date(2026, Calendar.MARCH, 1).time - 43_200_000, date(2026, Calendar.APRIL, 1).time - 43_200_000)
            .awaitValue().associate { it.categoryId to it.spent }
        assertEquals(mapOf<Int?, Long>(food to 3_000L, null to 700L), march)
    }

    @Test
    fun deletingACategoryKeepsItsTransactionsWithoutCategory() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val food = repository.addCategory("Food", "🍔", 1, 0)
        repository.addTransaction(bank, -100, "Lunch", Date(), categoryId = food)
        repository.addScheduled(bank, -50, "Gym", Date(System.currentTimeMillis() + 86_400_000L * 10), Repeat.MONTHLY, null, categoryId = food)
        repository.deleteCategory(food)
        assertNull(repository.getTransactionsOf(bank).single().categoryId)
        assertNull(repository.getScheduled(1)!!.categoryId)
        assertNull(repository.getCategory(food))
        assertEquals(-100L, total(bank))
    }

    @Test
    fun scheduledTransactionsKeepTheirCategoryWhenTheyAreApplied() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val bills = repository.addCategory("Bills", "💡", 1, 0)
        repository.addScheduled(bank, -1_000, "Internet", Date(System.currentTimeMillis() - 1_000), Repeat.NONE, null, categoryId = bills)
        assertEquals(bills, repository.getTransactionsOf(bank).single().categoryId)
    }

    @Test
    fun editingATransactionCanChangeItsCategory() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val food = repository.addCategory("Food", "🍔", 1, 0)
        repository.addTransaction(bank, -100, "Lunch", Date())
        val id = repository.getTransactionsOf(bank).single().transactionId
        repository.updateTransaction(id, -100, "Lunch", Date(), food)
        assertEquals(food, repository.getTransaction(id)!!.categoryId)
        repository.updateTransaction(id, -100, "Lunch", Date(), null)
        assertNull(repository.getTransaction(id)!!.categoryId)
    }

    @Test
    fun defaultCategoriesAreCreatedOnlyOnce() = runBlocking {
        val defaults = listOf(
            com.sanabria.vester.room.Category(name = "A", icon = "", color = 1),
            com.sanabria.vester.room.Category(name = "B", icon = "", color = 2),
        )
        assertTrue(repository.seedCategories(defaults))
        assertFalse(repository.seedCategories(defaults))
        assertEquals(2, repository.categories.awaitValue().size)
    }

    @Test
    fun partialDebtPaymentsReduceWhatIsLeftAndSettleAtTheTotal() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        repository.addDebt("Dinner · Ana", 5_000, Date(), person = "Ana", dueDate = Date(System.currentTimeMillis() - 86_400_000L))
        val id = db.debtDao().stats().firstId!!
        assertTrue(repository.getDebt(id)!!.isOverdue(System.currentTimeMillis()))

        repository.addTransaction(bank, 2_000, "Ana pays part", Date(), payDebtId = id)
        var debt = repository.getDebt(id)!!
        assertEquals(2_000L, debt.paid)
        assertEquals(3_000L, debt.remaining)
        assertTrue(debt.isActive)
        assertEquals(2_000L, total(bank))

        // Pagar de más no pasa del total y la deuda queda saldada
        repository.addTransaction(bank, 9_000, "Ana pays rest", Date(), payDebtId = id)
        debt = repository.getDebt(id)!!
        assertEquals(5_000L, debt.paid)
        assertFalse(debt.isActive)
        assertEquals(0L, debt.remaining)
    }

    @Test
    fun owedDebtsKeepTheirSignWhenPartiallyPaid() = runBlocking {
        val bank = repository.addAccount("Bank", 10_000, 0, "€")
        repository.addDebt("Car loan", -25_000, Date())
        val id = db.debtDao().stats().firstId!!
        repository.addTransaction(bank, -5_000, "Installment", Date(), payDebtId = id)
        assertEquals(-20_000L, repository.getDebt(id)!!.remaining)
    }

    @Test
    fun aDeletedDebtCanBeRestoredWithWhatWasPaid() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        repository.addDebt("Dinner", 4_000, Date())
        val id = db.debtDao().stats().firstId!!
        repository.addTransaction(bank, 1_000, "Part", Date(), payDebtId = id)
        val debt = repository.getDebt(id)!!
        repository.deleteDebt(id)
        assertNull(repository.getDebt(id))
        repository.restoreDebt(debt)
        assertEquals(debt, repository.getDebt(id))
    }

    @Test
    fun archivedAccountsLeaveTheListAndTheTotalButKeepTheirData() = runBlocking {
        val bank = repository.addAccount("Bank", 1_000, 0, "€")
        val old = repository.addAccount("Old", 500, 0, "€")
        repository.addTransaction(old, -100, "Coffee", Date())
        repository.setAccountArchived(old, true)
        assertEquals(listOf("Bank"), repository.getAccounts().map { it.name })
        assertEquals(1_000L, repository.stats().totalBalance.cents)
        assertEquals(listOf("Old"), repository.archivedAccounts.awaitValue().map { it.name })
        assertEquals(1, repository.getTransactionsOf(old).size)
        repository.setAccountArchived(old, false)
        assertEquals(listOf("Bank", "Old"), repository.getAccounts().map { it.name })
        assertEquals(1_400L, repository.stats().totalBalance.cents)
    }

    @Test
    fun aDeletedAccountCanBeRestoredWithItsTransactionsAndScheduled() = runBlocking {
        val bank = repository.addAccount("Bank", 1_000, 5, "€", "🏦")
        val cash = repository.addAccount("Cash", 0, 6, "€")
        repository.addTransaction(bank, -250, "Lunch", Date())
        repository.addScheduled(bank, -50, "Gym", Date(System.currentTimeMillis() + 86_400_000L * 10), Repeat.MONTHLY, null)
        val snapshot = repository.snapshotAccount(bank)!!
        repository.deleteAccount(bank)
        assertEquals(listOf("Cash"), repository.getAccounts().map { it.name })

        repository.restoreAccount(snapshot)
        val restored = repository.getAccount(bank)!!
        assertEquals("Bank" to 750L, restored.name to restored.total)
        assertEquals("🏦", restored.icon)
        assertEquals(listOf("Lunch"), repository.getTransactionsOf(bank).map { it.concept })
        assertEquals(1, repository.stats().scheduled.count)
        assertEquals(listOf("Cash", "Bank"), repository.getAccounts().map { it.name })
        assertEquals(750L, total(bank))
        assertEquals(0L, total(cash))
    }

    @Test
    fun goalsAreStoredAndAccumulateManualSavings() = runBlocking {
        val id = repository.addGoal("Trip", "✈️", 1, 100_000, null, null)
        repository.addToGoal(id, 20_000)
        repository.addToGoal(id, 5_000)
        assertEquals(25_000L, repository.getGoal(id)!!.saved)
        repository.updateGoal(id, "Trip to Japan", "✈️", 2, 150_000, null, null)
        assertEquals("Trip to Japan" to 150_000L, repository.getGoal(id)!!.let { it.name to it.target })
        repository.deleteGoal(id)
        assertNull(repository.getGoal(id))
    }

    @Test
    fun savingPaceIsTheNetChangeOfTheAccountSinceADate() = runBlocking {
        val bank = repository.addAccount("Savings", 0, 0, "€")
        val now = System.currentTimeMillis()
        repository.addTransaction(bank, 30_000, "Old", Date(now - 200L * 86_400_000L))
        repository.addTransaction(bank, 10_000, "Recent deposit", Date(now - 20L * 86_400_000L))
        repository.addTransaction(bank, -2_000, "Recent withdrawal", Date(now - 5L * 86_400_000L))
        assertEquals(8_000L, repository.accountNetSince(bank, now - 90L * 86_400_000L))
    }

    @Test
    fun deletingAnAccountKeepsItsGoalWithTheSavedAmount() = runBlocking {
        val bank = repository.addAccount("Savings", 12_000, 0, "€")
        val id = repository.addGoal("Phone", "📱", 1, 50_000, bank, null)
        repository.deleteAccount(bank)
        val goal = repository.getGoal(id)!!
        assertNull(goal.accountId)
        assertEquals(12_000L, goal.saved)
    }

    @Test
    fun groupsKeepMembersAndExpensesAndDeleteInCascade() = runBlocking {
        val id = repository.addGroup("Trip", "✈️", "Me", listOf("Ana", " Pablo ", ""))
        val members = repository.getGroupMembers(id)
        assertEquals(listOf("Me", "Ana", "Pablo"), members.map { it.name })
        assertEquals(listOf(true, false, false), members.map { it.isMe })

        val ids = members.map { it.memberId }
        repository.addGroupExpense(id, ids[0], 9_000, "Hotel", Date(), ids)
        repository.addGroupExpense(id, ids[1], 3_000, "Taxi", Date(), listOf(ids[0], ids[1]))
        val balances = com.sanabria.vester.datamanager.GroupBalances.balances(
            repository.groupExpenses(id).awaitValue(), ids,
        )
        assertEquals(mapOf(ids[0] to 4_500L, ids[1] to -1_500L, ids[2] to -3_000L), balances)

        // Un miembro con gastos no se puede quitar; uno sin gastos, sí
        assertFalse(repository.removeGroupMember(id, ids[1]))
        repository.addGroupMember(id, "Marta")
        val marta = repository.getGroupMembers(id).last()
        assertTrue(repository.removeGroupMember(id, marta.memberId))

        repository.deleteGroup(id)
        assertNull(repository.getGroup(id))
        assertEquals(0, repository.getGroupMembers(id).size)
        assertEquals(0, repository.allGroupExpenses.awaitValue().size)
    }

    @Test
    fun aDeletedGroupExpenseCanBeRestored() = runBlocking {
        val id = repository.addGroup("Flat", "", "Me", listOf("Ana"))
        val ids = repository.getGroupMembers(id).map { it.memberId }
        repository.addGroupExpense(id, ids[0], 1_000, "Rent", Date(), ids)
        val expense = repository.groupExpenses(id).awaitValue().single()
        repository.deleteGroupExpense(expense.expenseId)
        assertEquals(0, repository.groupExpenses(id).awaitValue().size)
        repository.restoreGroupExpense(expense)
        assertEquals(expense, repository.groupExpenses(id).awaitValue().single())
    }

    @Test
    fun notesAndPhotosAreStoredAndUpdated() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        repository.addTransaction(bank, -500, "Dinner", Date(), note = "with Ana\nsecond line", photo = "a.jpg")
        val saved = repository.getTransactionsOf(bank).single()
        assertEquals("with Ana\nsecond line" to "a.jpg", saved.note to saved.photo)
        repository.updateTransaction(saved.transactionId, -500, "Dinner", Date(), null, "changed", "")
        assertEquals("changed" to "", repository.getTransaction(saved.transactionId)!!.let { it.note to it.photo })
        assertEquals(emptySet<String>(), repository.usedPhotos())
    }

    @Test
    fun templatesAddTheirTransactionOnDemand() = runBlocking {
        val bank = repository.addAccount("Bank", 1_000, 0, "€")
        val food = repository.addCategory("Food", "🍔", 1, 0)
        repository.addTemplate("Coffee", -150, bank, food)
        val template = repository.templates.awaitValue().single()
        assertTrue(repository.applyTemplate(template.templateId))
        assertTrue(repository.applyTemplate(template.templateId))
        assertEquals(700L, total(bank))
        assertEquals(listOf("Coffee", "Coffee"), repository.getTransactionsOf(bank).map { it.concept })
        assertEquals(food, repository.getTransactionsOf(bank).first().categoryId)
        // Si la cuenta se borra, la plantilla también y ya no se puede apuntar
        repository.deleteAccount(bank)
        assertNull(db.templateDao().get(template.templateId))
        assertFalse(repository.applyTemplate(template.templateId))
    }

    @Test
    fun searchFiltersByTextKindAndAmount() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val cash = repository.addAccount("Cash", 0, 0, "€")
        repository.addTransaction(bank, -1_250, "Lunch with Ana", date(2026, Calendar.MARCH, 5))
        repository.addTransaction(bank, -300, "Coffee", date(2026, Calendar.MARCH, 6), note = "ana paid")
        repository.addTransaction(bank, 9_000, "Salary", date(2026, Calendar.MARCH, 1))
        repository.transfer(bank, cash, 1_000, date(2026, Calendar.MARCH, 8), "To cash", "From bank")
        fun find(filter: SearchFilter) = repository.search(filter).awaitValue().map { it.concept }.toSet()
        val all = SearchFilter()
        assertEquals(setOf("Lunch with Ana", "Coffee"), find(all.copy(text = "ana")))
        assertEquals(setOf("Salary"), find(all.copy(kind = SearchKind.INCOME)))
        assertEquals(setOf("To cash", "From bank"), find(all.copy(kind = SearchKind.TRANSFERS)))
        assertEquals(setOf("Lunch with Ana"), find(all.copy(minAbs = 1_000, maxAbs = 2_000, kind = SearchKind.PAYMENTS)))
        assertEquals(setOf("Coffee"), find(all.copy(from = date(2026, Calendar.MARCH, 6).time - 43_200_000, to = date(2026, Calendar.MARCH, 7).time - 43_200_000)))
    }

    @Test
    fun importingTheSameRowsTwiceDoesNotDuplicate() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        val rows = listOf(
            ImportedRow(date(2026, Calendar.MARCH, 5), "Lunch", -1_250),
            ImportedRow(date(2026, Calendar.MARCH, 6), "Salary", 9_000, note = "march"),
        )
        assertEquals(2, repository.importTransactions(bank, rows))
        assertEquals(0, repository.importTransactions(bank, rows))
        assertEquals(7_750L, total(bank))
        assertEquals(2, repository.getTransactionsOf(bank).size)
        assertEquals("march", repository.getTransactionsOf(bank).first { it.concept == "Salary" }.note)
    }

    @Test
    fun closingAGroupCreatesTheDebtsThatInvolveMe() = runBlocking {
        val id = repository.addGroup("Trip", "", "Me", listOf("Ana", "Pablo"))
        val ids = repository.getGroupMembers(id).map { it.memberId }
        // Yo pago 90 entre los tres y Ana paga 30 entre Ana y Pablo: Ana y Pablo me deben algo
        repository.addGroupExpense(id, ids[0], 9_000, "Hotel", Date(), ids)
        repository.addGroupExpense(id, ids[1], 3_000, "Taxi", Date(), listOf(ids[1], ids[2]))
        assertEquals(2, repository.closeGroup(id))
        assertTrue(repository.getGroup(id)!!.closed)
        val debts = repository.activeDebts.awaitValue()
        assertEquals(2, debts.size)
        assertTrue(debts.all { it.total > 0 && it.concept.startsWith("Trip · ") })
        assertEquals(setOf("Ana", "Pablo"), debts.map { it.person }.toSet())
        repository.reopenGroup(id)
        assertFalse(repository.getGroup(id)!!.closed)
    }

    @Test
    fun groupExpensesCanBeSplitByShares() = runBlocking {
        val id = repository.addGroup("Flat", "", "Me", listOf("Ana"))
        val ids = repository.getGroupMembers(id).map { it.memberId }
        repository.addGroupExpense(id, ids[0], 9_000, "Rent", Date(), ids, parts = mapOf(ids[0] to 1, ids[1] to 2))
        val expense = repository.groupExpenses(id).awaitValue().single()
        assertEquals(listOf(ids[0] to 1, ids[1] to 2), expense.participantParts)
        val balances = GroupBalances.balances(listOf(expense), ids)
        assertEquals(mapOf(ids[0] to 6_000L, ids[1] to -6_000L), balances)
    }

    @Test
    fun debtsWithDueDatesAreListedForReminders() = runBlocking {
        repository.addDebt("A", 1_000, Date(), dueDate = Date(System.currentTimeMillis() + 3_600_000))
        repository.addDebt("B", 1_000, Date())
        assertEquals(listOf("A"), repository.debtsWithDueDate().map { it.concept })
    }

    @Test
    fun partialPaymentsKeepAHistoryThatDiesWithTheDebt() = runBlocking {
        val bank = repository.addAccount("Bank", 0, 0, "€")
        repository.addDebt("Dinner", 5_000, Date())
        val id = db.debtDao().stats().firstId!!
        repository.addTransaction(bank, 2_000, "One", Date(), payDebtId = id)
        repository.addTransaction(bank, 9_000, "Two", Date(), payDebtId = id)
        // El segundo pago solo cuenta lo que faltaba
        assertEquals(listOf(2_000L, 3_000L), repository.getDebtPayments(id).map { it.amount })
        repository.deleteDebt(id)
        assertEquals(0, repository.getDebtPayments(id).size)
    }

    private fun date(year: Int, month: Int, day: Int): Date =
        Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0) }.time

    /** Primer valor de un LiveData, observándolo en el hilo principal. */
    private fun <T> LiveData<T>.awaitValue(): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        val observer = Observer<T> { result = it; latch.countDown() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { observeForever(observer) }
        latch.await(5, TimeUnit.SECONDS)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { removeObserver(observer) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
