package com.weather.metro.data.hko

import com.weather.metro.domain.LocationInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HkoClientIntegrationTest {
    @Test
    fun `live HKO and secondary endpoints produce a complete snapshot`() = runBlocking {
        assumeTrue(System.getenv("HKO_INTEGRATION_TEST") == "1")

        val snapshot = HkoClient().load(LocationInfo()).snapshot

        assertEquals("香港天文台", snapshot.location.stationName)
        assertTrue(snapshot.current.temperatureC != null)
        assertTrue(snapshot.current.weatherIconCode != null)
        assertTrue(snapshot.daily.size >= 7)
        assertTrue(snapshot.hourly.isNotEmpty())
        assertTrue(snapshot.overview.generalSituation.isNotBlank())
        assertTrue(snapshot.fetchedAtEpochMillis > 0)
    }
}
