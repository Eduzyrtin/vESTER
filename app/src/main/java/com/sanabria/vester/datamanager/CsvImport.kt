// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Qué columna del archivo es cada dato; -1 si el archivo no la tiene. */
data class CsvMapping(
    val date: Int = -1,
    val concept: Int = -1,
    val amount: Int = -1,
    val category: Int = -1,
    val note: Int = -1,
    val transfer: Int = -1,
)

/** Resultado de leer un archivo: las filas válidas y cuántas se han saltado por no entenderse la fecha o el importe. */
data class CsvImportResult(val rows: List<ImportedRow>, val skipped: Int)

/**
 * Lectura de archivos CSV de otras apps o del extracto del banco (y del propio de vESTER). Funciones puras: separan
 * columnas (coma, punto y coma o tabulador, con comillas), adivinan qué columna es cada cosa por su cabecera y
 * entienden las fechas y los importes más habituales ("2026-10-15", "15/10/2026", "1.234,56", "-12.50"...).
 */
object CsvImport {

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy/MM/dd",
    )

    /** Separa el texto en filas de columnas. El separador se elige por la primera línea. */
    fun parse(text: String): List<List<String>> {
        val clean = text.removePrefix(0xFEFF.toChar().toString())
        val firstLine = clean.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }?.takeIf { d -> firstLine.contains(d) } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < clean.length) {
            val c = clean[index]
            when {
                quoted && c == '"' && clean.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> {
                    row += cell.toString().trim()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && clean.getOrNull(index + 1) == '\n') index++
                    row += cell.toString().trim()
                    cell.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString().trim()
            if (row.any { it.isNotEmpty() }) rows += row
        }
        return rows
    }

    /** Adivina las columnas por los nombres de la cabecera (en español, inglés y algún otro idioma habitual). */
    fun guessMapping(header: List<String>): CsvMapping {
        fun find(vararg words: String): Int = header.indexOfFirst { h -> words.any { h.lowercase().contains(it) } }
        return CsvMapping(
            date = find("date", "fecha", "data", "datum", "dia"),
            concept = find("concept", "descri", "detalle", "payee", "memo", "libell", "beneficiario", "name", "nombre"),
            amount = find("amount", "importe", "value", "valor", "monto", "betrag", "montant", "total"),
            category = find("categor", "categ"),
            note = find("note", "nota", "comment"),
            transfer = find("transfer"),
        )
    }

    /** Fecha del texto con el primer formato que la entienda, o null. */
    fun parseDate(text: String): Date? {
        val value = text.trim()
        if (value.isEmpty()) return null
        for (pattern in DATE_FORMATS) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            val date = format.parse(value, position)
            if (date != null && position.index == value.length) return date
        }
        return null
    }

    /** Importe en céntimos del texto ("1.234,56", "1,234.56", "-12.5", "12 €"...), o null si no es un número. */
    fun parseAmount(text: String): Long? {
        var value = text.trim().replace(" ", "").replace(" ", "")
        if (value.isEmpty()) return null
        val negative = value.startsWith("-") || (value.startsWith("(") && value.endsWith(")")) || value.endsWith("-")
        value = value.filter { it.isDigit() || it == ',' || it == '.' }
        if (value.none { it.isDigit() }) return null
        val lastComma = value.lastIndexOf(',')
        val lastDot = value.lastIndexOf('.')
        val decimalAt = when {
            lastComma >= 0 && lastDot >= 0 -> maxOf(lastComma, lastDot)
            lastComma >= 0 -> lastComma.takeIf { value.length - it - 1 in 1..2 && value.count { c -> c == ',' } == 1 } ?: -1
            lastDot >= 0 -> lastDot.takeIf { value.length - it - 1 in 1..2 && value.count { c -> c == '.' } == 1 } ?: -1
            else -> -1
        }
        val digits = if (decimalAt < 0) value.filter { it.isDigit() } else value.substring(0, decimalAt).filter { it.isDigit() } + "." + value.substring(decimalAt + 1)
        val number = digits.toBigDecimalOrNull() ?: return null
        val cents = number.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return if (negative) -cents else cents
    }

    /**
     * Convierte las filas de datos (sin la cabecera) según [mapping]. [categoryIds] traduce el nombre de una categoría
     * (sin distinguir mayúsculas) a su id. Las filas sin fecha o sin importe válidos se cuentan como saltadas.
     */
    fun rows(data: List<List<String>>, mapping: CsvMapping, categoryIds: Map<String, Int>): CsvImportResult {
        val rows = mutableListOf<ImportedRow>()
        var skipped = 0
        val byName = categoryIds.mapKeys { it.key.lowercase() }
        fun cell(row: List<String>, index: Int) = if (index in row.indices) row[index] else ""
        for (row in data) {
            val date = parseDate(cell(row, mapping.date))
            val value = parseAmount(cell(row, mapping.amount))
            if (date == null || value == null) {
                skipped++
                continue
            }
            val concept = cell(row, mapping.concept).ifBlank { "—" }
            val transfer = cell(row, mapping.transfer).lowercase() in setOf("yes", "true", "1", "si", "sí", "oui", "sim")
            rows += ImportedRow(date, concept, value, byName[cell(row, mapping.category).lowercase()], cell(row, mapping.note), transfer)
        }
        return CsvImportResult(rows, skipped)
    }

    /** Solo para las pruebas: importe como BigDecimal en unidades. */
    internal fun toUnits(cents: Long): BigDecimal = BigDecimal.valueOf(cents, 2)
}
