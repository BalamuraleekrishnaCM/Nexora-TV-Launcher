package com.nexora.tv

internal object WeatherCache {
    fun canReuse(cachedCity: String?, city: String, cached: String?, lastFetch: Long, now: Long, force: Boolean): Boolean =
        !force && cachedCity == city && !cached.isNullOrBlank() && lastFetch > 0 && now >= lastFetch && now - lastFetch < 3_600_000L
}
