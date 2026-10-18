package com.sanabria.vester

import com.sanabria.vester.datamanager.CsvImport
import com.sanabria.vester.datamanager.CsvMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class CsvImportTest {

    @Test
    fun amountsInEuropeanAndAmericanFormats() {
        assertEquals(123_456L, CsvImport.parseAmount("1.234,56"))
        assertEquals(123_456L, CsvImport.parseAmount("1,234.56"))
        assertEquals(-1_250L, CsvImport.parseAmount("-12.50"))
        assertEquals(-1_250L, CsvImport.parseAmount("-12,5"))
        assertEquals(1_000L, CsvImport.parseAmount("10 €"))
        assertEquals(123_400L, CsvImport.parseAmount("1.234"))
        assertEquals(-5_000L, CsvImport.parseAmount("(50,00)"))
        assertEquals(9_999L, CsvImport.parseAmount("99.99"))
        assertNull(CsvImport.parseAmount("abc"))
        assertNull(CsvImport.parseAmount(""))
    }

    @Test
    fun datesInTheUsualFormats() {
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        assertEquals("2026-10-15 14:30", iso.format(CsvImport.parseDate("2026-10-15 14:30")!!))
        assertEquals("2026-10-15 00:00", iso.format(CsvImport.parseDate("15/10/2026")!!))
        assertEquals("2026-10-15 00:00", iso.format(CsvImport.parseDate("2026-10-15")!!))
        assertEquals("2026-10-15 00:00", iso.format(CsvImport.parseDate("15.10.2026")!!))
        assertNull(CsvImport.parseDate("31/02/2026"))
        assertNull(CsvImport.parseDate("hola"))
    }

    @Test
    fun parsesQuotesAndDelimiters() {
        val table = CsvImport.parse(0xFEFF.toChar() + "Date;Concept;Amount\n15/10/2026;\"Cena; con \"\"Ana\"\"\";-23,50\r\n16/10/2026;Café;-1,20\n\n")
        assertEquals(3, table.size)
        assertEquals(listOf("Date", "Concept", "Amount"), table[0])
        assertEquals(listOf("15/10/2026", "Cena; con \"Ana\"", "-23,50"), table[1])
        assertEquals("-1,20", table[2][2])
    }

    @Test
    fun guessesColumnsFromHeaders() {
        val mapping = CsvImport.guessMapping(listOf("Fecha", "Descripción", "Importe", "Categoría"))
        assertEquals(CsvMapping(date = 0, concept = 1, amount = 2, category = 3), mapping)
        val ours = CsvImport.guessMapping(listOf("Date", "Account", "Concept", "Amount", "Currency", "Category", "Transfer", "Note"))
        assertEquals(0, ours.date)
        assertEquals(2, ours.concept)
        assertEquals(3, ours.amount)
        assertEquals(5, ours.category)
        assertEquals(6, ours.transfer)
        assertEquals(7, ours.note)
    }

    @Test
    fun convertsRowsAndCountsTheOnesItCannotRead() {
        val table = CsvImport.parse(
            "Date,Concept,Amount,Category,Note,Transfer\n" +
                "2026-10-15 10:00,Lunch,-12.50,Food,with Ana,no\n" +
                "bad date,Coffee,-1.20,,,\n" +
                "2026-10-16 09:00,Salary,\"1,500.00\",Salary,,no\n" +
                "2026-10-17 09:00,Move,-100,,,yes\n",
        )
        val mapping = CsvImport.guessMapping(table.first())
        val result = CsvImport.rows(table.drop(1), mapping, mapOf("Food" to 7, "Salary" to 9))
        assertEquals(1, result.skipped)
        assertEquals(3, result.rows.size)
        assertEquals(-1_250L, result.rows[0].value)
        assertEquals(7, result.rows[0].categoryId)
        assertEquals("with Ana", result.rows[0].note)
        assertEquals(150_000L, result.rows[1].value)
        assertEquals(9, result.rows[1].categoryId)
        assertEquals(true, result.rows[2].transfer)
        assertNotNull(result.rows[2].date)
    }
}
