package com.weather.metro.data.location

import com.weather.metro.domain.LocationInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ObservationStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

private data class DistrictCentre(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

object HongKongStations {
    private val observationStations = listOf(
        ObservationStation("香港天文台", 22.3019, 114.1742),
        ObservationStation("京士柏", 22.3119, 114.1728),
        ObservationStation("香港公園", 22.2783, 114.1620),
        ObservationStation("黃竹坑", 22.2478, 114.1736),
        ObservationStation("跑馬地", 22.2708, 114.1831),
        ObservationStation("筲箕灣", 22.2814, 114.2361),
        ObservationStation("赤柱", 22.2142, 114.2186),
        ObservationStation("九龍城", 22.3282, 114.1887),
        ObservationStation("觀塘", 22.3157, 114.2261),
        ObservationStation("黃大仙", 22.3420, 114.1930),
        ObservationStation("深水埗", 22.3359, 114.1363),
        ObservationStation("啟德跑道公園", 22.3050, 114.2169),
        ObservationStation("沙田", 22.4025, 114.2103),
        ObservationStation("大埔", 22.4461, 114.1789),
        ObservationStation("將軍澳", 22.3157, 114.2553),
        ObservationStation("西貢", 22.3756, 114.2743),
        ObservationStation("屯門", 22.3911, 113.9767),
        ObservationStation("元朗公園", 22.4400, 114.0183),
        ObservationStation("流浮山", 22.4688, 113.9838),
        ObservationStation("打鼓嶺", 22.5286, 114.1567),
        ObservationStation("荃灣城門谷", 22.3757, 114.1261),
        ObservationStation("荃灣可觀", 22.3837, 114.1077),
        ObservationStation("青衣", 22.3480, 114.1090),
        ObservationStation("大美督", 22.4753, 114.2375),
        ObservationStation("長洲", 22.2011, 114.0267),
        ObservationStation("赤鱲角", 22.3094, 113.9219),
    )

    private val districts = listOf(
        DistrictCentre("中西區", 22.2820, 114.1449),
        DistrictCentre("灣仔", 22.2770, 114.1820),
        DistrictCentre("東區", 22.2841, 114.2241),
        DistrictCentre("南區", 22.2477, 114.1588),
        DistrictCentre("油尖旺", 22.3133, 114.1707),
        DistrictCentre("深水埗", 22.3307, 114.1622),
        DistrictCentre("九龍城", 22.3282, 114.1916),
        DistrictCentre("黃大仙", 22.3417, 114.1933),
        DistrictCentre("觀塘", 22.3133, 114.2258),
        DistrictCentre("葵青", 22.3549, 114.1276),
        DistrictCentre("荃灣", 22.3717, 114.1133),
        DistrictCentre("屯門", 22.3916, 113.9771),
        DistrictCentre("元朗", 22.4456, 114.0222),
        DistrictCentre("北區", 22.4940, 114.1380),
        DistrictCentre("大埔", 22.4500, 114.1688),
        DistrictCentre("沙田", 22.3872, 114.1953),
        DistrictCentre("西貢", 22.3814, 114.2705),
        DistrictCentre("離島區", 22.2554, 113.9442),
    )

    fun enrich(
        latitude: Double,
        longitude: Double,
        label: String,
        geocodedDistrict: String?,
        accuracyMetres: Int?,
    ): LocationInfo {
        val district = normalizeDistrict(geocodedDistrict)
            ?: districts.minBy { distanceKm(latitude, longitude, it.latitude, it.longitude) }.name
        val station = observationStations.minBy {
            distanceKm(latitude, longitude, it.latitude, it.longitude)
        }
        return LocationInfo(
            latitude = latitude,
            longitude = longitude,
            label = traditionalHongKongPlaceName(label).ifBlank { district }.let {
                if (locationKey(it) == locationKey(district)) district else it
            },
            district = district,
            stationName = station.name,
            tideStationCode = nearestTideStation(latitude, longitude),
            accuracyMetres = accuracyMetres,
        )
    }

    internal fun normalizeDistrict(value: String?): String? {
        val text = traditionalHongKongPlaceName(value.orEmpty())
            .lowercase()
            .replace(" district", "")
        if (text.isBlank()) return null
        return districts.firstOrNull { centre ->
            text.contains(centre.name.lowercase()) || when (centre.name) {
                "中西區" -> text.contains("central") || text.contains("western")
                "灣仔" -> text.contains("wan chai")
                "東區" -> text.contains("eastern")
                "南區" -> text.contains("southern")
                "油尖旺" -> text.contains("yau tsim mong")
                "深水埗" -> text.contains("sham shui po")
                "九龍城" -> text.contains("kowloon city")
                "黃大仙" -> text.contains("wong tai sin")
                "觀塘" -> text.contains("kwun tong")
                "葵青" -> text.contains("kwai tsing")
                "荃灣" -> text.contains("tsuen wan")
                "屯門" -> text.contains("tuen mun")
                "元朗" -> text.contains("yuen long")
                "北區" -> text == "north" || text.contains("north district")
                "大埔" -> text.contains("tai po")
                "沙田" -> text.contains("sha tin")
                "西貢" -> text.contains("sai kung")
                "離島區" -> text.contains("islands")
                else -> false
            }
        }?.name
    }

    private fun nearestTideStation(latitude: Double, longitude: Double): String {
        val tideStations = listOf(
            Triple("QUB", 22.2910, 114.2130),
            Triple("TPK", 22.4420, 114.1840),
            Triple("CLK", 22.3090, 113.9220),
            Triple("CCH", 22.2010, 114.0270),
            Triple("TAO", 22.2530, 113.8630),
            Triple("TBT", 22.4820, 114.0100),
            Triple("WAG", 22.1820, 114.3030),
        )
        return tideStations.minBy { (_, lat, lon) -> distanceKm(latitude, longitude, lat, lon) }.first
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

internal fun preferredLocationLabel(
    candidates: List<String?>,
    geocodedDistrict: String?,
): String {
    val district = HongKongStations.normalizeDistrict(geocodedDistrict)
    val districtKey = district?.let(::locationKey)
    return candidates.asSequence()
        .filterNotNull()
        .map(::traditionalHongKongPlaceName)
        .map { it.trim().trim(',', '，') }
        .filter { candidate ->
            val key = locationKey(candidate)
            key.isNotBlank() &&
                key.any(Char::isLetter) &&
                key != districtKey &&
                key !in BROAD_LOCATION_KEYS
        }
        .distinctBy(::locationKey)
        .firstOrNull()
        ?: district
        ?: "本地位置"
}

internal fun traditionalHongKongPlaceName(value: String): String = buildString(value.length) {
    value.forEach { character -> append(SIMPLIFIED_PLACE_CHARACTERS[character] ?: character) }
}

private fun locationKey(value: String): String = traditionalHongKongPlaceName(value)
    .lowercase()
    .replace(" district", "")
    .replace("香港特別行政區", "")
    .replace("區", "")
    .filter { it.isLetterOrDigit() }

private val BROAD_LOCATION_KEYS = setOf(
    "香港",
    "九龍",
    "新界",
    "hongkong",
    "kowloon",
    "newterritories",
)

private val SIMPLIFIED_PLACE_CHARACTERS = mapOf(
    '观' to '觀',
    '区' to '區',
    '湾' to '灣',
    '龙' to '龍',
    '黄' to '黃',
    '门' to '門',
    '贡' to '貢',
    '离' to '離',
    '岛' to '島',
    '东' to '東',
    '华' to '華',
    '马' to '馬',
    '头' to '頭',
    '厦' to '廈',
    '广' to '廣',
    '启' to '啟',
    '将' to '將',
    '军' to '軍',
    '长' to '長',
    '乐' to '樂',
    '业' to '業',
    '发' to '發',
    '丽' to '麗',
    '达' to '達',
    '狮' to '獅',
    '径' to '徑',
    '宝' to '寶',
    '顺' to '順',
    '伟' to '偉',
    '铁' to '鐵',
    '车' to '車',
    '凤' to '鳳',
    '围' to '圍',
    '义' to '義',
    '桥' to '橋',
    '滨' to '濱',
)
