// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import java.text.Normalizer
import java.util.Locale

/**
 * Detecta posibles movimientos en el texto de una notificación.
 *
 * Solo se leen las apps de [SOURCES]: carteras de pago (cualquier importe es un pago), bancos (si el texto habla
 * de un pago o un ingreso) y correo (solo si el asunto parece un recibo, una compra o un pago, para no confundir
 * publicidad con gastos). Lo detectado se guarda como sugerencia y nunca toca un saldo sin que el usuario lo confirme.
 */
object NotificationParser {

    enum class Kind { WALLET, BANK, EMAIL }

    data class Source(val name: String, val kind: Kind)

    /** Movimiento detectado: [value] en céntimos, negativo si es un pago. */
    data class Parsed(val value: Long, val concept: String)

    val SOURCES: Map<String, Source> = mapOf(
        // Carteras y pagos
        "com.google.android.apps.walletnfcrel" to Source("Google Wallet", Kind.WALLET),
        "com.google.android.apps.nbu.paisa.user" to Source("Google Pay", Kind.WALLET),
        "com.samsung.android.spay" to Source("Samsung Wallet", Kind.WALLET),
        "com.paypal.android.p2pmobile" to Source("PayPal", Kind.BANK),
        // Bancos
        "com.bbva.bbvacontigo" to Source("BBVA", Kind.BANK),
        "es.bancosantander.apps" to Source("Santander", Kind.BANK),
        "es.lacaixa.mobile.android.newwapicon" to Source("CaixaBank", Kind.BANK),
        "www.ingdirect.nativeframe" to Source("ING", Kind.BANK),
        "net.inverline.bancosabadell.officelocator.android" to Source("Sabadell", Kind.BANK),
        "com.bankinter.launcher" to Source("Bankinter", Kind.BANK),
        "es.openbank.mobile" to Source("Openbank", Kind.BANK),
        "es.evobanco.bancamovil" to Source("EVO Banco", Kind.BANK),
        "com.revolut.revolut" to Source("Revolut", Kind.BANK),
        "de.number26.android" to Source("N26", Kind.BANK),
        "com.transferwise.android" to Source("Wise", Kind.BANK),
        "com.bunq.android" to Source("bunq", Kind.BANK),
        "co.uk.getmondo" to Source("Monzo", Kind.BANK),
        "com.starlingbank.android" to Source("Starling", Kind.BANK),
        "de.traderepublic.app" to Source("Trade Republic", Kind.BANK),
        "es.ibercaja.ibercajaapp" to Source("Ibercaja", Kind.BANK),
        "com.squareup.cash" to Source("Cash App", Kind.WALLET),
        "com.venmo" to Source("Venmo", Kind.WALLET),
        // Correo
        "com.google.android.gm" to Source("Gmail", Kind.EMAIL),
        "com.microsoft.office.outlook" to Source("Outlook", Kind.EMAIL),
        "com.samsung.android.email.provider" to Source("Samsung Email", Kind.EMAIL),
        "com.yahoo.mobile.client.android.mail" to Source("Yahoo Mail", Kind.EMAIL),
        "ch.protonmail.android" to Source("Proton Mail", Kind.EMAIL),
    )

    private val INCOME_WORDS = listOf(
        "recibido", "has recibido", "te ha enviado", "te han enviado", "ingreso", "abono", "devolucion", "reembolso",
        "nomina", "received", "refund", "deposit", "sent you", "credited", "paid you",
    )
    private val PAYMENT_WORDS = listOf(
        "pago", "pagado", "compra", "cargo", "recibo", "factura", "pedido", "transferencia", "bizum", "retirada",
        "payment", "paid", "purchase", "receipt", "order", "invoice", "charged", "spent", "transaction", "transaccion",
    )

    private const val NUMBER = "\\d{1,3}(?:[.,\\s\\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?"
    private const val CURRENCY = "€|EUR|eur|euros?|US\\$|USD|\\$|£|GBP"
    private val AMOUNT = Regex("([-−+])?\\s*(?:(?:$CURRENCY)\\s?($NUMBER)|($NUMBER)\\s?(?:$CURRENCY))")
    private val MERCHANT = Regex("(?:\\ben\\b|\\bat\\b|\\bto\\b|\\bde\\b|\\bfrom\\b)\\s+([^.,\\n·•|]+?)(?=\\s+(?:con|with|por|el|on)\\b|[.,\\n·•|]|$)", RegexOption.IGNORE_CASE)

    /** Fuente conocida para [packageName], o null si sus notificaciones no se leen. */
    fun sourceOf(packageName: String): Source? = SOURCES[packageName]

    /** Analiza una notificación de una app de tipo [kind]. Devuelve null si no parece un movimiento. */
    fun parse(kind: Kind, title: String, text: String): Parsed? {
        val full = "$title\n$text"
        val plain = normalize(full)
        val isIncome = INCOME_WORDS.any { plain.contains(it) }
        val isPayment = PAYMENT_WORDS.any { plain.contains(it) }
        if (kind != Kind.WALLET && !isIncome && !isPayment) return null

        val match = AMOUNT.find(full) ?: return null
        val cents = parseAmount(match.groupValues[2].ifEmpty { match.groupValues[3] }) ?: return null
        if (cents <= 0) return null
        val sign = when {
            match.groupValues[1] == "+" -> 1
            match.groupValues[1].isNotEmpty() -> -1
            // En las carteras casi todo son pagos: solo es ingreso si es una devolución
            isIncome && (kind != Kind.WALLET || plain.contains("devolucion") || plain.contains("refund")) -> 1
            else -> -1
        }
        return Parsed(sign * cents, concept(kind, title, text, match.value))
    }

    /**
     * Convierte el número de una notificación a céntimos, sea "1.234,56", "1,234.56", "12,5" o "12".
     * El último separador seguido de una o dos cifras es el decimal; los demás separan miles.
     */
    fun parseAmount(number: String): Long? {
        val clean = number.replace(Regex("[\\s\\u00A0\\u202F]"), "")
        val lastSeparator = clean.indexOfLast { it == '.' || it == ',' }
        val decimals = if (lastSeparator >= 0) clean.substring(lastSeparator + 1) else ""
        val (integerPart, fraction) = if (lastSeparator >= 0 && decimals.length in 1..2) {
            clean.substring(0, lastSeparator) to decimals.padEnd(2, '0')
        } else {
            clean to "00"
        }
        val digits = integerPart.filter { it.isDigit() }.ifEmpty { "0" }
        return (digits + fraction).toLongOrNull()
    }

    /** Concepto para el movimiento: el comercio si se reconoce, si no el título de la notificación. */
    private fun concept(kind: Kind, title: String, text: String, amount: String): String {
        val titleWithoutAmount = title.replace(amount.trim(), "").trim(' ', '-', '·', ':')
        // "en Mercadona", "at Amazon", "de Juan"... descartando lo que empiece por cifra ("de 12,00 €")
        val merchant = (MERCHANT.findAll(text) + MERCHANT.findAll(title))
            .map { it.groupValues[1].trim() }
            .firstOrNull { it.firstOrNull()?.isLetter() == true }
        val concept = when {
            // En las carteras, si el título no lleva el importe, el título es el comercio
            kind == Kind.WALLET && !title.contains(amount.trim()) && title.isNotBlank() -> title.trim()
            !merchant.isNullOrBlank() -> merchant
            kind == Kind.EMAIL && text.isNotBlank() -> text.lineSequence().first()
            else -> titleWithoutAmount.ifEmpty { text.lineSequence().first() }
        }
        return concept.trim().take(MAX_CONCEPT)
    }

    /** Minúsculas y sin tildes, para buscar palabras clave. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private const val MAX_CONCEPT = 40
}
