package com.nexora.tv

import org.junit.Assert.assertEquals
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
}
