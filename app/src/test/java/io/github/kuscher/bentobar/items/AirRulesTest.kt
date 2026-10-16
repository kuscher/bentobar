package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirRulesTest {
    private val place = Place("47.37", "8.55")
    private val sampleJson = """
        {
          "current": {
            "us_aqi": 42,
            "european_aqi": 28,
            "pm2_5": 8.4,
            "pm10": 15.2,
            "ozone": 64.0,
            "uv_index": 6.3
          },
          "hourly": {
            "uv_index": [1.2, 4.5, 7.8, 6.1]
          }
        }
    """.trimIndent()

    @Test fun parsesOpenMeteoAirQualityReply() {
        val reading = AirRules.read(sampleJson, place, now = 10_000L)
        assertNotNull(reading)
        reading!!
        assertEquals(42, reading.usAqi)
        assertEquals(28, reading.euAqi)
        assertEquals(8.4, reading.pm25!!, 0.01)
        assertEquals(15.2, reading.pm10!!, 0.01)
        assertEquals(64.0, reading.ozone!!, 0.01)
        assertEquals(6.3, reading.uv!!, 0.01)
        assertEquals(7.8, reading.uvMax!!, 0.01)
    }

    @Test fun rejectsMalformedOrEmptyReply() {
        assertNull(AirRules.read("not json", place, 10_000L))
        assertNull(AirRules.read("""{"current":{}}""", place, 10_000L))
    }

    @Test fun classifiesUsAndEuAqiBandsAndUvLevels() {
        assertEquals(AirRules.Band.GOOD, AirRules.usBand(35))
        assertEquals(AirRules.Band.MODERATE, AirRules.usBand(75))
        assertEquals(AirRules.Band.SENSITIVE, AirRules.usBand(120))
        assertEquals(AirRules.Band.UNHEALTHY, AirRules.usBand(175))
        assertEquals(AirRules.Band.VERY_UNHEALTHY, AirRules.usBand(250))
        assertEquals(AirRules.Band.HAZARDOUS, AirRules.usBand(350))

        assertEquals(AirRules.Band.GOOD, AirRules.euBand(15))
        assertEquals(AirRules.Band.MODERATE, AirRules.euBand(35))
        assertEquals(AirRules.Band.HAZARDOUS, AirRules.euBand(120))

        assertEquals(AirRules.UvBand.LOW, AirRules.uvBand(1.5))
        assertEquals(AirRules.UvBand.MODERATE, AirRules.uvBand(4.0))
        assertEquals(AirRules.UvBand.HIGH, AirRules.uvBand(6.5))
        assertEquals(AirRules.UvBand.VERY_HIGH, AirRules.uvBand(9.0))
        assertEquals(AirRules.UvBand.EXTREME, AirRules.uvBand(11.5))
    }

    @Test fun evaluatesToneAndThresholdAlert() {
        val calm = AirRules.sample("good", 100_000L)!!
        assertEquals(Tone.NORMAL, AirRules.toneOf(AirRules.usBand(calm.usAqi!!), calm.uv))
        assertFalse(AirRules.active(calm, thresholdAqi = 100, eu = false))

        val smoke = AirRules.sample("smoke", 100_000L)!!
        assertEquals(Tone.ALERT, AirRules.toneOf(AirRules.usBand(smoke.usAqi!!), smoke.uv))
        assertTrue(AirRules.active(smoke, thresholdAqi = 100, eu = false))
    }
}
