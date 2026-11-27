package com.nexora.tv
import org.junit.Assert.*
import org.junit.Test

class WeatherCacheTest {
    @Test fun cityChangeInvalidates() = assertFalse(WeatherCache.canReuse("Kochi","Kottayam","25",100,200,false))
    @Test fun refreshInvalidates() = assertFalse(WeatherCache.canReuse("Kochi","Kochi","25",100,200,true))
    @Test fun recentCacheReused() = assertTrue(WeatherCache.canReuse("Kochi","Kochi","25",100,200,false))
    @Test fun expiredCacheInvalidates() = assertFalse(WeatherCache.canReuse("Kochi","Kochi","25",100,3_600_100,false))
    @Test fun futureTimestampInvalidates() = assertFalse(WeatherCache.canReuse("Kochi","Kochi","25",200,100,false))
}
