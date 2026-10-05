package io.github.kuscher.bentobar.util

import androidx.core.text.util.LocalePreferences
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Temperatures and speeds as the user reads them. Values come in metric (what the services send
 * and Android reports) and are converted here, so a unit choice never has to be sent anywhere and
 * applies at once. The arithmetic is pure; only [systemFahrenheit] asks Android.
 */
object Units {
    /** The countries that say temperatures in Fahrenheit (CLDR's measurement data): the fallback where Android has no answer. */
    private val FAHRENHEIT_COUNTRIES = setOf("US", "BS", "BZ", "KY", "PW", "PR", "GU", "VI", "AS", "MP", "UM", "FM", "MH", "LR")

    /**
     * Whether temperatures are said in Fahrenheit, for an item's "Temperature" choice: `c`, `f`, or
     * anything else for "Like the system".
     */
    fun fahrenheit(choice: String): Boolean = when (choice) {
        "c" -> false
        "f" -> true
        else -> systemFahrenheit()
    }

    /**
     * Android's answer: the user's regional preference (Android 14 and later: Settings › System ›
     * Languages › Regional preferences), else the locale's own unit.
     */
    fun systemFahrenheit(): Boolean {
        // Asked once a second by an item that shows a temperature, and the library builds a number
        // formatter each time: the answer is kept per locale. A changed regional preference is a
        // changed default locale (its unit rides in the locale's extension), so it is asked again then.
        val locale = Locale.getDefault(Locale.Category.FORMAT)
        system?.let { (asked, answer) -> if (asked == locale) return answer }
        return fahrenheit(runCatching { LocalePreferences.getTemperatureUnit() }.getOrNull(), locale.country).also { system = locale to it }
    }

    @Volatile private var system: Pair<Locale, Boolean>? = null

    /** [unit]: what Android said ("celsius", "fahrenhe", "kelvin", or nothing); [country]: the locale's, for when it said nothing. */
    fun fahrenheit(unit: String?, country: String): Boolean = when (unit) {
        LocalePreferences.TemperatureUnit.FAHRENHEIT -> true
        LocalePreferences.TemperatureUnit.CELSIUS, LocalePreferences.TemperatureUnit.KELVIN -> false
        else -> country.uppercase(Locale.ROOT) in FAHRENHEIT_COUNTRIES
    }

    fun toFahrenheit(celsius: Double): Double = celsius * 9 / 5 + 32

    /** [celsius] as a whole number in the user's unit. */
    fun whole(celsius: Double, fahrenheit: Boolean): Int = (if (fahrenheit) toFahrenheit(celsius) else celsius).roundToInt()

    /**
     * A temperature for the bar: a whole number, the degree sign, a real minus sign, and never a
     * minus before zero: "72°", "−4°", "0°".
     */
    fun degrees(celsius: Double, fahrenheit: Boolean): String = degrees(whole(celsius, fahrenheit))

    fun degrees(whole: Int): String = (if (whole < 0) "−" + (-whole) else whole.toString()) + "°"

    fun milesPerHour(kmh: Double): Double = kmh / 1.609344

    /** Wind is said in miles per hour where temperatures are in Fahrenheit, and in the United Kingdom. */
    fun windInMiles(fahrenheit: Boolean, country: String): Boolean = fahrenheit || country.uppercase(Locale.ROOT) == "GB"
}
