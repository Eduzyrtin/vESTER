package com.sanabria.vester

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanabria.vester.room.Account
import com.sanabria.vester.tools.Currencies
import com.sanabria.vester.tools.Total
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Suma de cuentas en monedas distintas con el cambio escrito a mano. */
@RunWith(AndroidJUnit4::class)
class CurrenciesTest {

    private fun account(total: Long, currency: String) = Account(name = "a", total = total, color = 0, currency = currency, position = 1)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("myPrefs", 0).edit().clear().commit()
        Currencies.init(context)
    }

    @After
    fun tearDown() {
        Currencies.setMainCurrency(null)
        Currencies.setRate("$", null)
        Currencies.setRate("£", null)
    }

    @Test
    fun oneCurrencyIsSummedAsIs() {
        assertEquals(Total(3_000, "€"), Currencies.total(listOf(account(1_000, "€"), account(2_000, "€"))))
    }

    @Test
    fun mixedCurrenciesWithoutRatesHaveNoCurrency() {
        Currencies.setMainCurrency("€")
        assertEquals(Total(3_000, null), Currencies.total(listOf(account(1_000, "€"), account(2_000, "$"))))
    }

    @Test
    fun mixedCurrenciesAreConvertedToTheMainOne() {
        Currencies.setMainCurrency("€")
        Currencies.setRate("$", BigDecimal("0.90"))
        Currencies.setRate("£", BigDecimal("1.15"))
        val total = Currencies.total(listOf(account(10_000, "€"), account(10_000, "$"), account(1_000, "£")))
        assertEquals(Total(10_000 + 9_000 + 1_150L, "€"), total)
        assertNull(Currencies.rate("¥"))
    }

    @Test
    fun ratesSurviveARestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Currencies.setMainCurrency("€")
        Currencies.setRate("$", BigDecimal("0.925"))
        Currencies.init(context)
        assertEquals("€", Currencies.mainCurrency)
        assertEquals(BigDecimal("0.925"), Currencies.rate("$"))
        assertEquals(9_250L, Currencies.toMain(10_000, "$"))
    }
}
