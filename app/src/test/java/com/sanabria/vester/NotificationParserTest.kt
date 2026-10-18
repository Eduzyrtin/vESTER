// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.NotificationParser
import com.sanabria.vester.tools.NotificationParser.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    @Test
    fun parseAmount_understandsSpanishAndEnglishFormats() {
        assertEquals(123456L, NotificationParser.parseAmount("1.234,56"))
        assertEquals(123456L, NotificationParser.parseAmount("1,234.56"))
        assertEquals(1250L, NotificationParser.parseAmount("12,5"))
        assertEquals(1200L, NotificationParser.parseAmount("12"))
        assertEquals(123400L, NotificationParser.parseAmount("1.234"))
        assertEquals(123456L, NotificationParser.parseAmount("1 234,56"))
    }

    @Test
    fun wallet_paymentUsesTheMerchantAsConcept() {
        val parsed = NotificationParser.parse(Kind.WALLET, "Mercadona", "12,34 € con Visa •• 1234")!!
        assertEquals(-1234L, parsed.value)
        assertEquals("Mercadona", parsed.concept)
    }

    @Test
    fun wallet_englishFormat() {
        val parsed = NotificationParser.parse(Kind.WALLET, "€8.50 at Starbucks", "Paid with Mastercard")!!
        assertEquals(-850L, parsed.value)
        assertEquals("Starbucks", parsed.concept)
    }

    @Test
    fun bank_incomeIsPositive() {
        val parsed = NotificationParser.parse(Kind.BANK, "Bizum recibido", "Has recibido 20,00 € de Juan Pérez")!!
        assertEquals(2000L, parsed.value)
        assertEquals("Juan Pérez", parsed.concept)
    }

    @Test
    fun bank_purchaseWithMerchant() {
        val parsed = NotificationParser.parse(Kind.BANK, "Compra con tarjeta", "Compra de 45,90 € en ZARA ESPAÑA con tu tarjeta ****1234")!!
        assertEquals(-4590L, parsed.value)
        assertEquals("ZARA ESPAÑA", parsed.concept)
    }

    @Test
    fun bank_ignoresTextsThatAreNotMovements() {
        assertNull(NotificationParser.parse(Kind.BANK, "Novedades", "Descubre la nueva cuenta con 100 € de regalo"))
    }

    @Test
    fun email_onlyReceiptsCount() {
        assertNull(NotificationParser.parse(Kind.EMAIL, "Tienda", "¡Ofertas desde 9,99 €!"))
        val parsed = NotificationParser.parse(Kind.EMAIL, "Amazon.es", "Confirmación de pedido: 23,45 €")!!
        assertEquals(-2345L, parsed.value)
    }

    @Test
    fun noAmountNoSuggestion() {
        assertNull(NotificationParser.parse(Kind.WALLET, "Google Wallet", "Tarjeta añadida correctamente"))
    }

    @Test
    fun walletRefundIsPositive() {
        val parsed = NotificationParser.parse(Kind.WALLET, "Devolución de IKEA", "Se ha devuelto 15,00 € a tu tarjeta")!!
        assertEquals(1500L, parsed.value)
    }

    @Test
    fun sources_areKnownAndNamesDoNotRepeat() {
        assertEquals(Kind.BANK, NotificationParser.sourceOf("com.bunq.android")?.kind)
        assertEquals(Kind.EMAIL, NotificationParser.sourceOf("ch.protonmail.android")?.kind)
        assertNull(NotificationParser.sourceOf("com.example.unknown"))
        val names = NotificationParser.SOURCES.values.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
