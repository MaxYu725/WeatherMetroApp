package com.weather.metro.data.hko

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastSourceTest {
    @Test
    fun `local and nine day forecast messages keep their own HKO sources`() {
        val raw = JSONObject()
            .put("rhr", JSONObject())
            .put(
                "fnd",
                JSONObject()
                    .put("generalSituation", "九天預報天氣概況")
                    .put("updateTime", "2026-08-02T07:50:00+08:00")
                    .put("weatherForecast", JSONArray()),
            )
            .put(
                "flw",
                JSONObject()
                    .put("generalSituation", "本港預報概況")
                    .put("forecastPeriod", "本港地區今日天氣預測")
                    .put("forecastDesc", "本港預報內容")
                    .put("tcInfo", "在下午三時，熱帶氣旋集結在香港以東。")
                    .put("outlook", "本港展望")
                    .put("updateTime", "2026-08-02T09:45:00+08:00"),
            )
            .put("warnsum", JSONObject())
            .put("warningInfo", JSONObject())
            .put("swt", JSONObject())
            .put(
                "openMeteo",
                JSONObject()
                    .put("current", JSONObject())
                    .put("daily", JSONObject()),
            )
            .put("sun", JSONObject())
            .put("moon", JSONObject())
            .put("tide", JSONObject())
            .put("visibility", JSONObject())
            .put(
                "location",
                JSONObject()
                    .put("latitude", 22.3019)
                    .put("longitude", 114.1742)
                    .put("label", "香港天文台")
                    .put("district", "油尖旺")
                    .put("stationName", "香港天文台")
                    .put("tideStationCode", "QUB"),
            )
            .put("fetchedAt", 1L)

        val snapshot = HkoClient().parseCached(raw.toString())

        assertEquals("九天預報天氣概況", snapshot.nineDayForecast.generalSituation)
        assertEquals("本港預報概況", snapshot.localForecast.generalSituation)
        assertEquals("本港預報內容", snapshot.localForecast.forecastDescription)
        assertEquals("在下午三時，熱帶氣旋集結在香港以東。", snapshot.localForecast.tropicalCycloneInfo)
        assertEquals("2026-08-02T07:50:00+08:00", snapshot.nineDayForecast.updatedAt)
        assertEquals("2026-08-02T09:45:00+08:00", snapshot.localForecast.updatedAt)
    }
}
