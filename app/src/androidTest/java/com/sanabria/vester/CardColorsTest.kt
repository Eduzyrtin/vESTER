package com.sanabria.vester

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Los 30 colores del selector tienen que dejar leer el texto de las tarjetas (WCAG AA, 4,5) de día y de noche. */
@RunWith(AndroidJUnit4::class)
class CardColorsTest {

    private val palette by lazy { InstrumentationRegistry.getInstrumentation().targetContext.accountPalette() }

    private fun contrast(text: Int, background: Int) = ColorUtils.calculateContrast(text, background)

    @Test
    fun dayTextIsReadableOnEveryColor() {
        assertTrue(palette.size >= 30)
        palette.forEach { color ->
            val ratio = contrast(primaryTextOn(color), color)
            assertTrue("#%06X day contrast %.1f".format(color and 0xFFFFFF, ratio), ratio >= 4.5)
        }
    }

    @Test
    fun nightTextIsReadableOnEveryColor() {
        palette.forEach { color ->
            val night = nightCardColor(color)
            val ratio = contrast(primaryTextOn(night), night)
            assertTrue("#%06X night contrast %.1f".format(color and 0xFFFFFF, ratio), ratio >= 4.5)
        }
    }

    @Test
    fun nightColorsStayDistinguishableAndDark() {
        val night = palette.map { nightCardColor(it) }
        night.forEach { assertTrue("night color too light", ColorUtils.calculateLuminance(it) < 0.25) }
        // Dos colores distintos de la paleta no pueden acabar casi iguales de noche
        for (i in night.indices) for (j in i + 1 until night.size) {
            val a = night[i]
            val b = night[j]
            val distance = Math.sqrt(
                Math.pow((Color.red(a) - Color.red(b)).toDouble(), 2.0) +
                    Math.pow((Color.green(a) - Color.green(b)).toDouble(), 2.0) +
                    Math.pow((Color.blue(a) - Color.blue(b)).toDouble(), 2.0),
            )
            // Los pasteles hermanos (dos amarillos, dos azules...) pueden parecerse, pero no ser idénticos
            assertTrue("night colors ${i + 1} and ${j + 1} are identical", distance > 3)
        }
    }
}
