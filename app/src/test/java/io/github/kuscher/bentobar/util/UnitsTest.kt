package io.github.kuscher.bentobar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Temperatures and wind as the user reads them, from the metric values that come in. */
class UnitsTest {
    @Test fun androidsAnswerDecidesTheUnit() {
        // What LocalePreferences says for the user's regional preference: it wins over the country.
        assertTrue(Units.fahrenheit("fahrenhe", "DE"))
        assertFalse(Units.fahrenheit("celsius", "US"))
        assertFalse(Units.fahrenheit("kelvin", "US"))
    }

    @Test fun withoutAnAnswerTheCountryDecides() {
        for (unit in listOf(null, "", "something new")) {
            assertTrue(Units.fahrenheit(unit, "US"))
            assertTrue(Units.fahrenheit(unit, "us"))
            assertTrue(Units.fahrenheit(unit, "BS"))
            assertFalse(Units.fahrenheit(unit, "GB"))
            assertFalse(Units.fahrenheit(unit, "DE"))
            assertFalse(Units.fahrenheit(unit, ""))
        }
    }

    @Test fun anItemsOwnChoiceWinsOverTheSystem() {
        assertFalse(Units.fahrenheit("c"))
        assertTrue(Units.fahrenheit("f"))
    }

    @Test fun degreesAreWholeWithARealMinusAndNoMinusZero() {
        assertEquals("72°", Units.degrees(22.2, fahrenheit = true))
        assertEquals("22°", Units.degrees(22.2, fahrenheit = false))
        assertEquals("−4°", Units.degrees(-4.4, fahrenheit = false))
        assertEquals("−4°", Units.degrees(-20.0, fahrenheit = true))
        assertEquals("0°", Units.degrees(-0.4, fahrenheit = false))
        assertEquals("0°", Units.degrees(-17.8, fahrenheit = true)) // 0 °F, give or take
        assertEquals("93°", Units.degrees(34.0, fahrenheit = true)) // a battery at 34 °C
        assertEquals("34°", Units.degrees(34.2, fahrenheit = false))
        assertEquals("−12°", Units.degrees(-12))
        // The minus is the real sign, not a hyphen.
        assertEquals('−', Units.degrees(-4).first())
    }

    @Test fun conversions() {
        assertEquals(32.0, Units.toFahrenheit(0.0), 1e-9)
        assertEquals(212.0, Units.toFahrenheit(100.0), 1e-9)
        assertEquals(-40.0, Units.toFahrenheit(-40.0), 1e-9)
        assertEquals(72, Units.whole(22.2, fahrenheit = true))
        assertEquals(9.0, Units.milesPerHour(14.484096), 1e-6)
    }

    @Test fun windIsInMilesWhereTemperaturesAreInFahrenheitAndInTheUnitedKingdom() {
        assertTrue(Units.windInMiles(fahrenheit = true, country = "US"))
        assertTrue(Units.windInMiles(fahrenheit = false, country = "GB"))
        assertTrue(Units.windInMiles(fahrenheit = false, country = "gb"))
        assertFalse(Units.windInMiles(fahrenheit = false, country = "DE"))
        assertFalse(Units.windInMiles(fahrenheit = false, country = "IE"))
    }
}
