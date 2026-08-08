package com.weather.metro.data.hko

import com.weather.metro.domain.AlertSeverity
import com.weather.metro.domain.AstronomyInfo
import com.weather.metro.domain.CurrentConditions
import com.weather.metro.domain.DailyForecast
import com.weather.metro.domain.LocalForecast
import com.weather.metro.domain.LocationInfo
import com.weather.metro.domain.NineDayForecast
import com.weather.metro.domain.TideEvent
import com.weather.metro.domain.WeatherAlert
import com.weather.metro.domain.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.roundToInt

data class WeatherNetworkResult(
    val snapshot: WeatherSnapshot,
    val cachePayload: String,
)

class HkoClient {
    suspend fun load(location: LocationInfo): WeatherNetworkResult = coroutineScope {
        val today = LocalDate.now(HONG_KONG_ZONE)
        val rhr = async { fetchWeather("rhrread") }
        val fnd = async { fetchWeather("fnd") }
        val flw = async { fetchWeather("flw") }
        val warnsum = async { fetchWeather("warnsum") }
        val warningInfo = async { fetchWeather("warningInfo") }
        val swt = async { optionalJson { fetchWeather("swt") } }
        val openMeteo = async { fetchOpenMeteo(location) }
        val sun = async { optionalJson { fetchOpenData("SRS", today) } }
        val moon = async { optionalJson { fetchOpenData("MRS", today) } }
        val tide = async { optionalJson { fetchOpenData("HLT", today, location.tideStationCode) } }
        val visibility = async { optionalJson { fetchOpenData("LTMV", today) } }

        val raw = JSONObject()
            .put("rhr", rhr.await())
            .put("fnd", fnd.await())
            .put("flw", flw.await())
            .put("warnsum", warnsum.await())
            .put("warningInfo", warningInfo.await())
            .put("swt", swt.await())
            .put("openMeteo", openMeteo.await())
            .put("sun", sun.await())
            .put("moon", moon.await())
            .put("tide", tide.await())
            .put("visibility", visibility.await())
            .put("location", location.toJson())
            .put("fetchedAt", System.currentTimeMillis())

        WeatherNetworkResult(
            snapshot = parseSnapshot(raw, stale = false),
            cachePayload = raw.toString(),
        )
    }

    fun parseCached(payload: String): WeatherSnapshot = parseSnapshot(JSONObject(payload), stale = true)

    private fun parseSnapshot(raw: JSONObject, stale: Boolean): WeatherSnapshot {
        val location = raw.getJSONObject("location").toLocationInfo()
        val rhr = raw.getJSONObject("rhr")
        val fnd = raw.getJSONObject("fnd")
        val flw = raw.getJSONObject("flw")
        val openMeteo = raw.getJSONObject("openMeteo")
        val visibility = parseVisibility(raw.optJSONObject("visibility"))

        return WeatherSnapshot(
            location = location,
            current = parseCurrent(rhr, openMeteo, fnd, location, visibility),
            alerts = WarningNormalizer.normalize(
                raw.getJSONObject("warnsum"),
                raw.getJSONObject("warningInfo"),
                raw.optJSONObject("swt") ?: JSONObject(),
            ),
            localForecast = parseLocalForecast(flw),
            nineDayForecast = parseNineDayForecast(fnd),
            astronomy = parseAstronomy(
                raw.optJSONObject("sun"),
                raw.optJSONObject("moon"),
                raw.optJSONObject("tide"),
                LocalDate.now(HONG_KONG_ZONE),
            ),
            fetchedAtEpochMillis = raw.optLong("fetchedAt", System.currentTimeMillis()),
            isStale = stale,
        )
    }

    private suspend fun fetchWeather(dataType: String): JSONObject = getJson(
        "$HKO_WEATHER_URL?dataType=$dataType&lang=tc",
    )

    private suspend fun fetchOpenData(
        dataType: String,
        date: LocalDate,
        station: String? = null,
    ): JSONObject {
        val stationParam = station?.let { "&station=${encode(it)}" }.orEmpty()
        return getJson(
            "$HKO_OPEN_DATA_URL?dataType=$dataType&rformat=json" +
                "&year=${date.year}&month=${date.monthValue}&day=${date.dayOfMonth}$stationParam",
        )
    }

    private suspend fun fetchOpenMeteo(location: LocationInfo): JSONObject {
        val current = listOf(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "dew_point_2m",
            "surface_pressure",
            "weather_code",
            "wind_speed_10m",
            "wind_gusts_10m",
            "wind_direction_10m",
        ).joinToString(",")
        val daily = "temperature_2m_max,temperature_2m_min"
        return getJson(
            "$OPEN_METEO_URL?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=$current&daily=$daily" +
                "&timezone=Asia%2FHong_Kong&forecast_days=3",
        )
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "WeatherMetroApp/1.0 (Android)")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from ${URI(url).host}")
            val text = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun optionalJson(block: suspend () -> JSONObject): JSONObject =
        runCatching { block() }.getOrDefault(JSONObject())

    private fun parseCurrent(
        rhr: JSONObject,
        openMeteo: JSONObject,
        fnd: JSONObject,
        location: LocationInfo,
        visibilityKm: Double?,
    ): CurrentConditions {
        val current = openMeteo.optJSONObject("current") ?: JSONObject()
        val daily = openMeteo.optJSONObject("daily") ?: JSONObject()
        val hkoTemperature = findStationValue(
            rhr.optJSONObject("temperature")?.optJSONArray("data"),
            location.stationName,
        )
        val humidity = findStationValue(
            rhr.optJSONObject("humidity")?.optJSONArray("data"),
            location.stationName,
        ) ?: firstValue(rhr.optJSONObject("humidity")?.optJSONArray("data"))
        val rain = findStationMax(
            rhr.optJSONObject("rainfall")?.optJSONArray("data"),
            location.district,
        )
        val uvItem = rhr.optJSONObject("uvindex")?.optJSONArray("data")?.optJSONObject(0)
        val icon = rhr.optJSONArray("icon")?.optInt(0)?.takeIf { it > 0 }

        return CurrentConditions(
            temperatureC = hkoTemperature ?: current.optNullableDouble("temperature_2m"),
            feelsLikeC = current.optNullableDouble("apparent_temperature"),
            humidityPercent = humidity?.roundToInt()
                ?: current.optNullableDouble("relative_humidity_2m")?.roundToInt(),
            minTemperatureC = daily.optJSONArray("temperature_2m_min")?.optNullableDouble(0),
            maxTemperatureC = daily.optJSONArray("temperature_2m_max")?.optNullableDouble(0),
            weatherIconCode = icon,
            windDirection = windDirectionName(current.optNullableDouble("wind_direction_10m")),
            windSpeedKmh = current.optNullableDouble("wind_speed_10m")?.roundToInt(),
            gustKmh = current.optNullableDouble("wind_gusts_10m")?.roundToInt(),
            rainfallMm = rain,
            uvIndex = uvItem?.optNullableDouble("value"),
            uvDescription = uvItem?.optString("desc")?.takeIf { it.isNotBlank() },
            visibilityKm = visibilityKm,
            pressureHpa = current.optNullableDouble("surface_pressure"),
            dewPointC = current.optNullableDouble("dew_point_2m"),
            observedAt = rhr.optString("updateTime").takeIf { it.isNotBlank() }
                ?: fnd.optString("updateTime").takeIf { it.isNotBlank() },
        )
    }

    private fun parseDaily(fnd: JSONObject): List<DailyForecast> {
        val rows = fnd.optJSONArray("weatherForecast") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val date = item.optString("forecastDate")
                add(
                    DailyForecast(
                        date = if (date.length == 8) "${date.substring(6, 8)}/${date.substring(4, 6)}" else date,
                        weekday = WEEKDAY_MAP[item.optString("week")] ?: item.optString("week"),
                        iconCode = item.optInt("ForecastIcon"),
                        minTemperatureC = item.nestedValue("forecastMintemp"),
                        maxTemperatureC = item.nestedValue("forecastMaxtemp"),
                        minHumidityPercent = item.nestedValue("forecastMinrh").roundToInt(),
                        maxHumidityPercent = item.nestedValue("forecastMaxrh").roundToInt(),
                        precipitationProbability = item.optString("PSR").ifBlank { "--" },
                        wind = item.optString("forecastWind"),
                        description = item.optString("forecastWeather"),
                    ),
                )
            }
        }
    }

    private fun parseLocalForecast(flw: JSONObject) = LocalForecast(
        generalSituation = flw.optString("generalSituation"),
        forecastPeriod = flw.optString("forecastPeriod"),
        forecastDescription = flw.optString("forecastDesc"),
        outlook = flw.optString("outlook"),
        tropicalCycloneInfo = flw.optString("tcInfo")
            .ifBlank { flw.optString("tropicalCycloneInfo") },
        fireDangerWarning = flw.optString("fireDangerWarning"),
        updatedAt = flw.optString("updateTime"),
    )

    private fun parseNineDayForecast(fnd: JSONObject) = NineDayForecast(
        generalSituation = fnd.optString("generalSituation"),
        days = parseDaily(fnd),
        updatedAt = fnd.optString("updateTime"),
    )

    private fun parseAstronomy(
        sun: JSONObject?,
        moon: JSONObject?,
        tide: JSONObject?,
        date: LocalDate,
    ): AstronomyInfo {
        val sunRow = sun.firstDataRow()
        val moonRow = moon.firstDataRow()
        val (phase, illumination) = moonPhase(date)
        return AstronomyInfo(
            sunrise = sunRow?.optString(1)?.displayValue(),
            solarTransit = sunRow?.optString(2)?.displayValue(),
            sunset = sunRow?.optString(3)?.displayValue(),
            moonrise = moonRow?.optString(1)?.displayValue(),
            moonTransit = moonRow?.optString(2)?.displayValue(),
            moonset = moonRow?.optString(3)?.displayValue(),
            moonPhase = phase,
            moonIlluminationPercent = illumination,
            tides = parseTides(tide),
        )
    }

    private fun parseTides(json: JSONObject?): List<TideEvent> {
        val rows = json?.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONArray(index) ?: continue
                val values = (0 until row.length()).map { row.optString(it) }
                val time = values.firstOrNull { it.matches(Regex("\\d{1,2}:\\d{2}")) } ?: continue
                val height = values.firstNotNullOfOrNull { it.toDoubleOrNull() }
                val type = values.lastOrNull { it.contains("潮") || it.contains("High", true) || it.contains("Low", true) }
                    ?: "潮汐"
                add(TideEvent(time = time, heightMetres = height, type = type))
            }
        }.take(6)
    }

    private fun parseVisibility(json: JSONObject?): Double? {
        val rows = json?.optJSONArray("data") ?: return null
        for (index in 0 until rows.length()) {
            val row = rows.optJSONArray(index) ?: continue
            for (column in row.length() - 1 downTo 0) {
                row.optString(column).toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun findStationValue(data: JSONArray?, stationName: String): Double? {
        if (data == null) return null
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            if (item.optString("place") == stationName) return item.optNullableDouble("value")
        }
        return null
    }

    private fun findStationMax(data: JSONArray?, district: String): Double? {
        if (data == null) return null
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            if (item.optString("place") == district) return item.optNullableDouble("max")
        }
        return null
    }

    private fun firstValue(data: JSONArray?): Double? = data?.optJSONObject(0)?.optNullableDouble("value")

    private fun moonPhase(date: LocalDate): Pair<String, Int> {
        val referenceNewMoon = Instant.parse("2000-01-06T18:14:00Z")
        val instant = date.atStartOfDay(HONG_KONG_ZONE).toInstant()
        val days = (instant.epochSecond - referenceNewMoon.epochSecond) / 86_400.0
        val age = ((days % SYNODIC_MONTH) + SYNODIC_MONTH) % SYNODIC_MONTH
        val fraction = age / SYNODIC_MONTH
        val illumination = ((1 - cos(2 * Math.PI * fraction)) / 2 * 100).roundToInt()
        val label = when {
            fraction < 0.03 || fraction >= 0.97 -> "新月"
            fraction < 0.22 -> "眉月"
            fraction < 0.28 -> "上弦月"
            fraction < 0.47 -> "盈凸月"
            fraction < 0.53 -> "滿月"
            fraction < 0.72 -> "虧凸月"
            fraction < 0.78 -> "下弦月"
            else -> "殘月"
        }
        return label to illumination
    }

    private fun windDirectionName(degrees: Double?): String? {
        if (degrees == null) return null
        val names = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")
        return names[((degrees / 45.0).roundToInt() % 8 + 8) % 8]
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val HKO_WEATHER_URL = "https://data.weather.gov.hk/weatherAPI/opendata/weather.php"
        private const val HKO_OPEN_DATA_URL = "https://data.weather.gov.hk/weatherAPI/opendata/opendata.php"
        private const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
        private const val SYNODIC_MONTH = 29.53058867
        private val HONG_KONG_ZONE = ZoneId.of("Asia/Hong_Kong")
        private val WEEKDAY_MAP = mapOf(
            "星期一" to "MON",
            "星期二" to "TUE",
            "星期三" to "WED",
            "星期四" to "THU",
            "星期五" to "FRI",
            "星期六" to "SAT",
            "星期日" to "SUN",
        )
    }
}

object WarningNormalizer {
    fun normalize(summary: JSONObject, detailsJson: JSONObject, swtJson: JSONObject): List<WeatherAlert> {
        val details = detailsJson.optJSONArray("details") ?: JSONArray()
        val alerts = mutableListOf<WeatherAlert>()
        val keys = summary.keys()
        while (keys.hasNext()) {
            val family = keys.next()
            val item = summary.optJSONObject(family) ?: continue
            val action = item.optString("actionCode", "ISSUE").uppercase()
            if (action == "CANCEL") continue
            val code = item.optString("code", family)
            val detail = findDetail(details, family, code)
            val title = item.optString("name").ifBlank { warningName(code) }
            val content = detail?.optJSONArray("contents").joinNonBlank("\n\n")
                .ifBlank { item.optString("type").ifBlank { title } }
            val updatedAt = item.optString("updateTime")
                .ifBlank { detail?.optString("updateTime").orEmpty() }
            alerts += WeatherAlert(
                id = "warning:$code",
                code = code,
                title = item.optString("type").ifBlank { title },
                content = content,
                updatedAt = updatedAt,
                severity = severity(code),
                iconUrl = warningIconUrl(code),
                actionCode = action,
            )
        }

        val swt = swtJson.optJSONArray("swt") ?: JSONArray()
        for (index in 0 until swt.length()) {
            val tip = swt.optJSONObject(index) ?: continue
            val text = tip.optString("desc").trim()
            if (text.isBlank()) continue
            alerts += WeatherAlert(
                id = "swt:${text.stableHash()}",
                code = "SWT",
                title = "特別天氣提示",
                content = text,
                updatedAt = tip.optString("updateTime"),
                severity = if (URGENT_TIP_WORDS.containsMatchIn(text)) AlertSeverity.WARNING else AlertSeverity.TIP,
                actionCode = "UPDATE",
                isTip = true,
            )
        }

        return alerts.distinctBy { it.id }.sortedBy { it.severity.ordinal }
    }

    private fun findDetail(details: JSONArray, family: String, code: String): JSONObject? {
        for (index in 0 until details.length()) {
            val item = details.optJSONObject(index) ?: continue
            val statement = item.optString("warningStatementCode")
            val subtype = item.optString("subtype")
            if (subtype == code || statement == family || statement == familyForCode(code)) return item
        }
        return null
    }

    private fun severity(code: String): AlertSeverity = when {
        code.matches(Regex("TC(8.*|9|10)")) || code in setOf("WRAINR", "WRAINB", "WTMW") -> AlertSeverity.URGENT
        code in setOf("WRAINA", "WTS", "TC3", "WL", "WFIRER") -> AlertSeverity.WARNING
        else -> AlertSeverity.ADVISORY
    }

    private fun familyForCode(code: String): String = when {
        code.startsWith("TC") -> "WTCSGNL"
        code.startsWith("WRAIN") -> "WRAIN"
        code.startsWith("WFIRE") -> "WFIRE"
        code == "WFNW" || code == "WFNTSA" -> "WFNTSA"
        code == "WMSGN" || code == "WMSGNL" -> "WMSGNL"
        else -> code
    }

    private fun warningName(code: String): String = WARNING_NAMES[code] ?: "天氣警告"

    private fun warningIconUrl(code: String): String? = OFFICIAL_WARNING_ICON_URLS[code]

    private fun String.stableHash(): String = hashCode().toUInt().toString(16)

    private val URGENT_TIP_WORDS = Regex("水浸|猛烈陣風|冰雹|水龍卷|山泥傾瀉")
    private val WARNING_NAMES = mapOf(
        "WTS" to "雷暴警告",
        "WRAINA" to "黃色暴雨警告",
        "WRAINR" to "紅色暴雨警告",
        "WRAINB" to "黑色暴雨警告",
        "TC1" to "一號戒備信號",
        "TC3" to "三號強風信號",
        "TC8NE" to "八號東北烈風或暴風信號",
        "TC8SE" to "八號東南烈風或暴風信號",
        "TC8NW" to "八號西北烈風或暴風信號",
        "TC8SW" to "八號西南烈風或暴風信號",
        "TC9" to "九號烈風或暴風風力增強信號",
        "TC10" to "十號颶風信號",
        "WHOT" to "酷熱天氣警告",
        "WCOLD" to "寒冷天氣警告",
        "WMSGNL" to "強烈季候風信號",
        "WL" to "山泥傾瀉警告",
        "WFROST" to "霜凍警告",
        "WFIREY" to "黃色火災危險警告",
        "WFIRER" to "紅色火災危險警告",
        "WTMW" to "海嘯警告",
        "WFNTSA" to "新界北部水浸特別報告",
    )
    private const val OFFICIAL_WARNING_ICON_BASE =
        "https://www.hko.gov.hk/tc/wxinfo/dailywx/images/"
    private val OFFICIAL_WARNING_ICON_URLS = mapOf(
        "WHOT" to "vhot.gif",
        "WRAINA" to "raina.gif",
        "WRAINR" to "rainr.gif",
        "WRAINB" to "rainb.gif",
        "TC1" to "tc1.gif",
        "TC3" to "tc3.gif",
        "TC8SE" to "tc8b.gif",
        "TC8SW" to "tc8c.gif",
        "TC8NE" to "tc8ne.gif",
        "TC8NW" to "tc8d.gif",
        "TC9" to "tc9.gif",
        "TC10" to "tc10.gif",
        "WTS" to "ts.gif",
        "WFNW" to "ntfl.gif",
        "WFNTSA" to "ntfl.gif",
        "WL" to "landslip.gif",
        "WCOLD" to "cold.gif",
        "WMSGN" to "sms.gif",
        "WMSGNL" to "sms.gif",
        "WFROST" to "frost.gif",
        "WFIRER" to "firer.gif",
        "WFIREY" to "firey.gif",
        "WTMW" to "tsunami-warn.gif",
    ).mapValues { (_, fileName) ->
        OFFICIAL_WARNING_ICON_BASE + fileName
    }
}

private fun LocationInfo.toJson() = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("label", label)
    .put("district", district)
    .put("stationName", stationName)
    .put("tideStationCode", tideStationCode)
    .put("accuracyMetres", accuracyMetres)

private fun JSONObject.toLocationInfo() = LocationInfo(
    latitude = optDouble("latitude", 22.3019),
    longitude = optDouble("longitude", 114.1742),
    label = optString("label", "香港天文台"),
    district = optString("district", "油尖旺"),
    stationName = optString("stationName", "香港天文台"),
    tideStationCode = optString("tideStationCode", "QUB"),
    accuracyMetres = if (isNull("accuracyMetres")) null else optInt("accuracyMetres"),
)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private fun JSONArray.optNullableDouble(index: Int): Double? =
    if (index !in 0 until length() || isNull(index)) null else optDouble(index).takeUnless { it.isNaN() }

private fun JSONObject.valueAt(key: String, index: Int): Double =
    optJSONArray(key)?.optNullableDouble(index) ?: 0.0

private fun JSONObject.nestedValue(key: String): Double =
    optJSONObject(key)?.optNullableDouble("value") ?: 0.0

private fun JSONObject?.firstDataRow(): JSONArray? = this?.optJSONArray("data")?.optJSONArray(0)

private fun JSONArray?.joinNonBlank(separator: String): String {
    if (this == null) return ""
    return buildList {
        for (index in 0 until length()) optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(separator)
}

private fun String?.displayValue(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "----" }
