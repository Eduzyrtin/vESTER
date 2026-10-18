// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {

    private val spanish = Locale.forLanguageTag("es-ES")

    @Test
    fun parseMoney_keepsEveryCent() {
        // Con Double, 0.29 * 100 se truncaba a 28 y 1.15 * 100 a 114
        assertEquals(29L, parseMoney("0.29"))
        assertEquals(115L, parseMoney("1.15"))
        assertEquals(1999L, parseMoney("19.99"))
        assertEquals(4010L, parseMoney("40.1"))
        // Recorre todos los céntimos de 0 a 100 €: ninguno puede perder un céntimo
        for (cents in 0L..10_000L) {
            val text = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
            assertEquals(text, cents, parseMoney(text))
        }
    }

    @Test
    fun parseMoney_acceptsCommaAndPoint() {
        assertEquals(1250L, parseMoney("12,5"))
        assertEquals(1250L, parseMoney("12.50"))
        assertEquals(1200L, parseMoney("12"))
        assertEquals(50L, parseMoney(",5"))
        assertEquals(1200L, parseMoney(" 12 "))
    }

    @Test
    fun parseMoney_rejectsInvalidText() {
        assertNull(parseMoney(""))
        assertNull(parseMoney("."))
        assertNull(parseMoney("abc"))
        assertNull(parseMoney("1.2.3"))
        assertNull(parseMoney(null))
    }

    @Test
    fun formatMoney_alwaysShowsTwoDecimals() {
        assertEquals("12,50", formatMoney(1250, spanish))
        assertEquals("-0,05", formatMoney(-5, spanish))
        assertEquals("1.234.567,89", formatMoney(123456789, spanish))
        assertEquals("12.50", formatMoney(1250, Locale.US))
        assertEquals("0.00", formatMoney(0, Locale.US))
    }

    @Test
    fun formatMoneyInput_roundTripsThroughParse() {
        assertEquals("12345,67", formatMoneyInput(-1234567, spanish))
        assertEquals("0.05", formatMoneyInput(5, Locale.US))
        for (cents in listOf(0L, 1L, 29L, 115L, 1999L, 123456789L)) {
            assertEquals(cents, parseMoney(formatMoneyInput(cents, spanish)))
            assertEquals(cents, parseMoney(formatMoneyInput(cents, Locale.US)))
        }
    }

    @Test
    fun divideMoney_roundsTheOddCentUp() {
        assertEquals(500L, divideMoney(1000, 2))
        assertEquals(501L, divideMoney(1001, 2))
        assertEquals(-501L, divideMoney(-1001, 2))
        assertEquals(333L, divideMoney(1000, 3))
        assertEquals(1700L, divideMoney(5100, 3))
        assertEquals(1275L, divideMoney(5100, 4))
    }
}
