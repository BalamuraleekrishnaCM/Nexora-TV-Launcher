package com.nexora.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.roundToInt

internal data class Weather(
    val place: String,
    val temperature: Int,
    val code: Int,
    val high: Int,
    val low: Int
) {
    val condition: String
        get() = when (code) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67, in 80..82 -> "Rain"
            in 71..77, 85, 86 -> "Snow"
            in 95..99 -> "Thunderstorms"
            else -> "Conditions unavailable"
        }

    fun toJson(): String = JSONObject()
        .put("place", place)
        .put("temperature", temperature)
        .put("code", code)
        .put("high", high)
        .put("low", low)
        .toString()

    companion object {
        fun fromJson(json: String): Weather = JSONObject(json).run {
            Weather(getString("place"), getInt("temperature"), getInt("code"), getInt("high"), getInt("low"))
        }

        fun fetch(city: String): Weather {
            val query = URLEncoder.encode(city, "UTF-8")
            val location = readJson("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=en")
                .optJSONArray("results")?.optJSONObject(0) ?: throw UnknownWeatherCity()
            val latitude = location.getDouble("latitude")
            val longitude = location.getDouble("longitude")
            val forecast = readJson(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=1"
            )
            val current = forecast.getJSONObject("current")
            val daily = forecast.getJSONObject("daily")
            val place = listOf(location.getString("name"), location.optString("admin1"), location.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            return Weather(
                place,
                current.getDouble("temperature_2m").roundToInt(),
                current.getInt("weather_code"),
                daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
            )
        }

        private fun readJson(address: String): JSONObject {
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }
    }
}

internal class UnknownWeatherCity : Exception()
