package com.sanabria.vester

import com.sanabria.vester.datamanager.GroupBalances
import com.sanabria.vester.datamanager.Transfer
import com.sanabria.vester.room.GroupExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GroupBalancesTest {

    private fun expense(payer: Int, amount: Long, vararg participants: Int, settlement: Boolean = false) = GroupExpense(
        groupId = 1, payerId = payer, amount = amount, concept = "x", date = Date(0),
        participants = GroupExpense.joinParticipants(participants.toList()), settlement = settlement,
    )

    @Test
    fun anEvenSplitLeavesThePayerOwedByTheOthers() {
        val balances = GroupBalances.balances(listOf(expense(1, 9_000, 1, 2, 3)), listOf(1, 2, 3))
        assertEquals(mapOf(1 to 6_000L, 2 to -3_000L, 3 to -3_000L), balances)
    }

    @Test
    fun theExtraCentsAreGivenOutAndBalancesAlwaysSumToZero() {
        val balances = GroupBalances.balances(listOf(expense(1, 1_000, 1, 2, 3), expense(2, 501, 1, 2)), listOf(1, 2, 3))
        assertEquals(0L, balances.values.sum())
        // 1000 entre 3: 334 + 333 + 333; 501 entre 2: 251 + 250
        assertEquals(1_000L - 334 - 251, balances[1])
        assertEquals(501L - 333 - 250, balances[2])
        assertEquals(-333L, balances[3])
    }

    @Test
    fun settlementsPayTheDebtsWithTheFewestTransfers() {
        val balances = mapOf(1 to 6_000L, 2 to -3_000L, 3 to -3_000L)
        assertEquals(
            setOf(Transfer(2, 1, 3_000), Transfer(3, 1, 3_000)),
            GroupBalances.settlements(balances).toSet(),
        )
        // Una cadena A debe a B y B debe a C se resuelve con un solo pago si cuadra
        assertEquals(listOf(Transfer(1, 3, 500)), GroupBalances.settlements(mapOf(1 to -500L, 2 to 0L, 3 to 500L)))
    }

    @Test
    fun applyingTheSuggestedSettlementsLeavesEveryoneAtZero() {
        val expenses = listOf(
            expense(1, 12_000, 1, 2, 3, 4),
            expense(2, 3_550, 2, 3),
            expense(3, 7_777, 1, 3, 4),
            expense(4, 999, 1, 2, 3, 4),
        )
        val ids = listOf(1, 2, 3, 4)
        val balances = GroupBalances.balances(expenses, ids)
        val payments = GroupBalances.settlements(balances).map { expense(it.from, it.amount, it.to, settlement = true) }
        val after = GroupBalances.balances(expenses + payments, ids)
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun aSettlementIsJustAnExpenseForTheReceiver() {
        val balances = GroupBalances.balances(listOf(expense(1, 4_000, 1, 2), expense(2, 2_000, 1, settlement = true)), listOf(1, 2))
        assertEquals(mapOf(1 to 0L, 2 to 0L), balances)
    }
}

class GroupBalancesPartsTest {
    private fun expense(payer: Int, amount: Long, participants: String) = GroupExpense(
        groupId = 1, payerId = payer, amount = amount, concept = "x", date = Date(0), participants = participants,
    )

    @Test
    fun partsSplitUnevenly() {
        // Ana (1) paga 90: Ana 2 partes, Pablo (2) 1 parte → 60 y 30
        val balances = GroupBalances.balances(listOf(expense(1, 9_000, "1:2,2:1")), listOf(1, 2))
        assertEquals(mapOf(1 to 3_000L, 2 to -3_000L), balances)
    }

    @Test
    fun partsRoundingStillSumsToZero() {
        val balances = GroupBalances.balances(listOf(expense(3, 1_000, "1:1,2:1,3:1"), expense(1, 1_001, "1:2,2:3")), listOf(1, 2, 3))
        assertEquals(0L, balances.values.sum())
    }

    @Test
    fun partsAreParsedAndJoinedBackCompatibly() {
        val e = expense(1, 100, "4:2,5")
        assertEquals(listOf(4 to 2, 5 to 1), e.participantParts)
        assertEquals(listOf(4, 5), e.participantIds)
        assertEquals("4,5", GroupExpense.joinParts(listOf(4 to 1, 5 to 1)))
        assertEquals("4:2,5:1", GroupExpense.joinParts(listOf(4 to 2, 5 to 1)))
    }
}
