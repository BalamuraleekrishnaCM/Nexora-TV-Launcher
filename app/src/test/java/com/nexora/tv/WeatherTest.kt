package com.nexora.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    @Test
    fun displaysForecastConditionFromProviderCode() {
        val conditions = mapOf(0 to "Clear", 3 to "Overcast", 48 to "Fog", 63 to "Rain",
            86 to "Snow", 96 to "Thunderstorms", -1 to "Conditions unavailable")
        conditions.forEach { (code, expected) ->
            assertEquals(expected, Weather("Kochi", 29, code, 31, 24).condition)
        }
    }

    @Test
    fun matchesEverySuppliedRegionWithoutChangingCase() {
        val qualifiers = listOf("Kerala", "in")
        assertTrue(matchesWeatherRegion(qualifiers, listOf("Kerala", "India", "IN")))
        assertFalse(matchesWeatherRegion(qualifiers, listOf("Kochi", "Japan", "JP")))
        assertTrue(matchesWeatherRegion(emptyList(), emptyList()))
    }
}
