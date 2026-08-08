package com.weather.metro.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HongKongStationsTest {
    @Test
    fun `english geocoder district is normalised to traditional chinese`() {
        val location = HongKongStations.enrich(
            latitude = 22.3133,
            longitude = 114.2258,
            label = "Kwun Tong",
            geocodedDistrict = "Kwun Tong District",
            accuracyMetres = 8,
        )

        assertEquals("觀塘", location.district)
        assertEquals(8, location.accuracyMetres)
    }

    @Test
    fun `simplified geocoder district is normalised to traditional chinese`() {
        val location = HongKongStations.enrich(
            latitude = 22.3133,
            longitude = 114.2258,
            label = "观塘区",
            geocodedDistrict = "观塘区",
            accuracyMetres = 6,
        )

        assertEquals("觀塘", location.label)
        assertEquals("觀塘", location.district)
    }

    @Test
    fun `street is preferred over duplicate district labels`() {
        val label = preferredLocationLabel(
            candidates = listOf(null, "鴻圖道", "观塘", "观塘区", "九龙"),
            geocodedDistrict = "观塘区 九龙",
        )

        assertEquals("鴻圖道", label)
    }

    @Test
    fun `nearest official observation station is selected from coordinates`() {
        val location = HongKongStations.enrich(
            latitude = 22.4025,
            longitude = 114.2103,
            label = "",
            geocodedDistrict = null,
            accuracyMetres = null,
        )

        assertEquals("沙田", location.stationName)
        assertTrue(location.label.isNotBlank())
    }
}
