package com.weather.metro.data.hko

import com.weather.metro.domain.AlertSeverity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningNormalizerTest {
    @Test
    fun `active warning uses matching details and official code`() {
        val summary = JSONObject()
            .put(
                "WRAIN",
                JSONObject()
                    .put("code", "WRAINR")
                    .put("type", "紅色暴雨警告信號")
                    .put("actionCode", "ISSUE")
                    .put("updateTime", "2026-08-01T11:05:00+08:00"),
            )
        val details = JSONObject().put(
            "details",
            JSONArray().put(
                JSONObject()
                    .put("warningStatementCode", "WRAIN")
                    .put("subtype", "WRAINR")
                    .put("contents", JSONArray().put("新界部分地區錄得大雨。")),
            ),
        )

        val alert = WarningNormalizer.normalize(summary, details, JSONObject()).single()

        assertEquals("WRAINR", alert.code)
        assertEquals(AlertSeverity.URGENT, alert.severity)
        assertTrue(alert.content.contains("新界部分地區"))
        assertTrue(alert.iconUrl.orEmpty().contains("warn800_10"))
    }

    @Test
    fun `cancelled summary rows are never shown as active`() {
        val summary = JSONObject().put(
            "WTS",
            JSONObject()
                .put("code", "WTS")
                .put("actionCode", "CANCEL"),
        )

        assertTrue(WarningNormalizer.normalize(summary, JSONObject(), JSONObject()).isEmpty())
    }

    @Test
    fun `special weather tip gets stable identity and elevated severity when urgent`() {
        val tips = JSONObject().put(
            "swt",
            JSONArray().put(
                JSONObject()
                    .put("desc", "預料香港部分地區可能出現猛烈陣風。")
                    .put("updateTime", "2026-08-01T12:15:00+08:00"),
            ),
        )

        val first = WarningNormalizer.normalize(JSONObject(), JSONObject(), tips).single()
        val second = WarningNormalizer.normalize(JSONObject(), JSONObject(), tips).single()

        assertEquals(first.id, second.id)
        assertEquals(AlertSeverity.WARNING, first.severity)
        assertTrue(first.isTip)
        assertFalse(first.id.endsWith(":0"))
    }
}
