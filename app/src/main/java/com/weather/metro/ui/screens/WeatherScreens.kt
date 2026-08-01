package com.weather.metro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.metro.data.settings.UiSettings
import com.weather.metro.domain.AlertSeverity
import com.weather.metro.domain.AstronomyInfo
import com.weather.metro.domain.HourlyWeather
import com.weather.metro.domain.WeatherAlert
import com.weather.metro.domain.WeatherSnapshot
import com.weather.metro.ui.components.ExpandableMetroTile
import com.weather.metro.ui.components.HkoRemoteImage
import com.weather.metro.ui.components.MetroSectionLabel
import com.weather.metro.ui.components.MetroStat
import com.weather.metro.ui.components.MetroTile
import com.weather.metro.ui.theme.LocalMetroAccent
import com.weather.metro.ui.theme.LocalMetroSubText
import com.weather.metro.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CurrentScreen(
    snapshot: WeatherSnapshot,
    accent: Color,
    onRefresh: () -> Unit,
    onRequestLocation: () -> Unit,
) {
    var heroExpanded by remember { mutableStateOf(false) }
    var overviewExpanded by remember { mutableStateOf(false) }
    val current = snapshot.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(if (snapshot.isStale) Color(0xFFF09609) else Color(0xFF00C853)))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (snapshot.isStale) "顯示離線快取" else "香港天文台資料已同步",
                    color = LocalMetroSubText.current,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("refresh", color = accent, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onRefresh))
            }
        }

        item {
            ExpandableMetroTile(
                seed = "current:${snapshot.location.district}",
                background = accent,
                expanded = heroExpanded,
                onExpandedChange = { heroExpanded = it },
                collapsed = {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(snapshot.location.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                        Text(
                            "${snapshot.location.district} · ${snapshot.location.stationName}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                        )
                    }
                    Text(if (heroExpanded) "−" else "+", color = Color.White, fontSize = 25.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = current.temperatureC?.let { "${it.roundToInt()}°" } ?: "--°",
                        color = Color.White,
                        fontSize = 72.sp,
                        lineHeight = 72.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.weight(1f),
                    )
                    HkoRemoteImage(
                        url = current.weatherIconCode?.let(::hkoWeatherIconUrl),
                        contentDescription = "香港天文台天氣圖示",
                        modifier = Modifier.size(92.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "濕度 ${current.humidityPercent.display("%")}" +
                        "  ·  ↓ ${current.minTemperatureC.display("°")}  ↑ ${current.maxTemperatureC.display("°")}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
                },
                expandedContent = {
                StatGrid(
                    listOf(
                        Triple("體感溫度", current.feelsLikeC.display("°C"), true),
                        Triple("風向風速", "${current.windDirection ?: "--"} ${current.windSpeedKmh.display(" km/h")}", true),
                        Triple("最高陣風", current.gustKmh.display(" km/h"), true),
                        Triple("所在地區雨量", current.rainfallMm.display(" mm"), false),
                        Triple("紫外線", listOfNotNull(current.uvIndex?.toString(), current.uvDescription).joinToString(" ").ifBlank { "--" }, false),
                        Triple("能見度", current.visibilityKm.display(" km"), false),
                        Triple("氣壓", current.pressureHpa.display(" hPa"), true),
                        Triple("露點", current.dewPointC.display("°C"), true),
                    ),
                )
                snapshot.location.accuracyMetres?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "定位精度 ±${it} m · 點按重新定位",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onRequestLocation),
                    )
                }
                AstronomyPanel(snapshot.astronomy)
                },
            )
        }

        item {
            ExpandableMetroTile(
                seed = "overview",
                background = Color(0xFF242424),
                expanded = overviewExpanded,
                onExpandedChange = { overviewExpanded = it },
                collapsed = {
                Text("天氣概況", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(9.dp))
                Text(
                    snapshot.overview.generalSituation.ifBlank { "香港天文台暫未提供概況。" },
                    color = Color.White,
                    maxLines = if (overviewExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 26.sp,
                )
                Text(formatHkoTime(snapshot.overview.updatedAt), color = LocalMetroSubText.current, fontSize = 11.sp)
                },
                expandedContent = {
                OverviewSection(snapshot.overview.forecastPeriod, snapshot.overview.forecastDescription)
                OverviewSection("展望", snapshot.overview.outlook)
                OverviewSection("熱帶氣旋消息", snapshot.overview.tropicalCycloneInfo)
                OverviewSection("火險", snapshot.overview.fireDangerWarning)
                },
            )
        }

        item { MetroSectionLabel("alerts & tips") }
        item { AlertsSection(snapshot.alerts) }
    }
}

@Composable
private fun AstronomyPanel(info: AstronomyInfo) {
    Spacer(Modifier.height(22.dp))
    Text("astronomy", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(10.dp))
    StatGrid(
        listOf(
            Triple("日出", info.sunrise ?: "--", false),
            Triple("日中", info.solarTransit ?: "--", false),
            Triple("日落", info.sunset ?: "--", false),
            Triple("月出", info.moonrise ?: "--", false),
            Triple("月中", info.moonTransit ?: "--", false),
            Triple("月落", info.moonset ?: "--", false),
            Triple("月相", info.moonPhase ?: "--", true),
            Triple("月面照明", info.moonIlluminationPercent.display("%"), true),
        ),
    )
    if (info.tides.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text("鄰近潮汐站", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        info.tides.forEach { tide ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(tide.time, color = Color.White, modifier = Modifier.width(70.dp), fontSize = 18.sp)
                Text(tide.type, color = Color.White, modifier = Modifier.weight(1f))
                Text(tide.heightMetres.display(" m"), color = Color.White)
            }
        }
    }
}

@Composable
private fun StatGrid(items: List<Triple<String, String, Boolean>>) {
    items.chunked(2).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            rowItems.forEach { (label, value, secondary) ->
                MetroStat(label, value, Modifier.weight(1f), secondary)
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun OverviewSection(title: String, body: String) {
    if (body.isBlank()) return
    Text(
        title.ifBlank { "預測" },
        color = Color.Black,
        modifier = Modifier.background(Color.White).padding(horizontal = 7.dp, vertical = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(body, color = Color.White, lineHeight = 27.sp, textAlign = TextAlign.Justify)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun AlertsSection(alerts: List<WeatherAlert>) {
    var selectedId by remember(alerts) { mutableStateOf<String?>(null) }
    if (alerts.isEmpty()) {
        MetroTile(seed = "no-alerts", background = Color(0xFF222222), modifier = Modifier.fillMaxWidth()) {
            Text("現時沒有生效的天氣警告或特別提示。", color = LocalMetroSubText.current)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alerts.chunked(4).forEachIndexed { groupIndex, group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.forEach { alert ->
                    AlertSmallTile(
                        alert = alert,
                        selected = alert.id == selectedId,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedId = if (selectedId == alert.id) null else alert.id },
                    )
                }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
            val selected = group.firstOrNull { it.id == selectedId }
            if (selected != null) AlertDetailTile(selected, groupIndex)
        }
    }
}

@Composable
private fun AlertSmallTile(
    alert: WeatherAlert,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    MetroTile(
        seed = alert.id,
        background = alertColor(alert.severity),
        modifier = modifier.aspectRatio(0.70f),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                HkoRemoteImage(
                    url = alert.iconUrl,
                    contentDescription = alert.title,
                    modifier = Modifier.size(30.dp),
                    fallback = if (alert.isTip) "i" else "!",
                )
                Spacer(Modifier.weight(1f))
                Text(if (selected) "−" else "+", color = Color.White, fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                alert.title,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatHkoTime(alert.updatedAt), color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun AlertDetailTile(alert: WeatherAlert, groupIndex: Int) {
    val requester = remember { BringIntoViewRequester() }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(alert.id) {
        if (!reduceMotion) delay(180)
        requester.bringIntoView()
    }
    MetroTile(
        seed = "detail:${alert.id}",
        background = alertColor(alert.severity),
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(alert.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                    Text(
                        "${alert.actionCode.lowercase()} · ${formatHkoTime(alert.updatedAt)}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                }
                HkoRemoteImage(alert.iconUrl, alert.title, Modifier.size(58.dp), if (alert.isTip) "i" else "!")
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Text(alert.content, color = Color.White, lineHeight = 27.sp, textAlign = TextAlign.Justify)
        }
    }
}

@Composable
fun HourlyScreen(hourly: List<HourlyWeather>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Open-Meteo secondary estimate", color = LocalMetroSubText.current, fontSize = 11.sp) }
        itemsIndexed(hourly, key = { _, item -> item.epochMillis }) { index, item ->
            var expanded by remember(item.epochMillis) { mutableStateOf(false) }
            ExpandableMetroTile(
                seed = "hourly:${item.epochMillis}",
                background = if (index % 2 == 0) Color(0xFF202020) else Color(0xFF292929),
                expanded = expanded,
                onExpandedChange = { expanded = it },
                collapsed = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, color = Color.White, fontSize = 22.sp, modifier = Modifier.width(72.dp))
                    Text(wmoGlyph(item.weatherCode), color = Color.White, fontSize = 28.sp)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${item.temperatureC.roundToInt()}°", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Light)
                        Text("雨 ${item.precipitationProbability}%", color = LocalMetroAccent.current, fontSize = 11.sp)
                    }
                }
                },
                expandedContent = {
                StatGrid(
                    listOf(
                        Triple("體感", "${item.apparentTemperatureC.roundToInt()}°C", true),
                        Triple("濕度", "${item.humidityPercent}%", true),
                        Triple("降水", "${item.precipitationMm} mm", true),
                        Triple("風向風速", "${item.windDirection} ${item.windSpeedKmh} km/h", true),
                        Triple("紫外線", item.uvIndex.toString(), true),
                    ),
                )
                },
            )
        }
    }
}

@Composable
fun ForecastScreen(snapshot: WeatherSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (snapshot.overview.generalSituation.isNotBlank()) {
            item {
                MetroTile("forecast-summary", Color(0xFF242424), Modifier.fillMaxWidth()) {
                    Column {
                        Text("香港天文台天氣概況", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(9.dp))
                        Text(snapshot.overview.generalSituation, color = Color.White, lineHeight = 26.sp, textAlign = TextAlign.Justify)
                    }
                }
            }
        }
        itemsIndexed(snapshot.daily, key = { _, day -> day.date }) { index, day ->
            var expanded by remember(day.date) { mutableStateOf(false) }
            ExpandableMetroTile(
                seed = "forecast:${day.date}",
                background = if (index == 0) LocalMetroAccent.current else Color(0xFF252525),
                expanded = expanded,
                onExpandedChange = { expanded = it },
                collapsed = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(76.dp)) {
                        Text(day.weekday, color = if (index == 0) Color.White else LocalMetroAccent.current, fontSize = 22.sp)
                        Text(day.date, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    HkoRemoteImage(hkoWeatherIconUrl(day.iconCode), day.description, Modifier.size(44.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${day.minTemperatureC.roundToInt()}° – ${day.maxTemperatureC.roundToInt()}°",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
                },
                expandedContent = {
                Text(day.description, color = Color.White, lineHeight = 27.sp, textAlign = TextAlign.Justify)
                Spacer(Modifier.height(12.dp))
                Text(day.wind, color = Color.White.copy(alpha = 0.82f))
                Text(
                    "濕度 ${day.minHumidityPercent}–${day.maxHumidityPercent}% · 顯著降雨概率 ${day.precipitationProbability}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
                },
            )
        }
    }
}

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val tools = listOf(
        Triple("rainfall", "定點降雨及閃電預報", "https://maps.weather.gov.hk/ocf/index_uc.html?data=ncrf"),
        Triple("radar", "香港天文台雷達圖像", "https://www.hko.gov.hk/tc/wxinfo/radars/radar-range.htm"),
        Triple("cyclone", "熱帶氣旋位置及路徑", "https://www.hko.gov.hk/tc/wxinfo/currwx/tc_gis.htm"),
        Triple("lightning", "閃電位置資訊", "https://maps.weather.gov.hk/llis/llis.htm"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { Text("官方香港天文台工具將於瀏覽器開啟。", color = LocalMetroSubText.current) }
        itemsIndexed(tools) { index, tool ->
            MetroTile(
                seed = tool.first,
                background = if (index == 0) LocalMetroAccent.current else Color(0xFF252525),
                modifier = Modifier.fillMaxWidth().height(142.dp),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.third))) },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Text("official tool", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(tool.second, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Light)
                    Text("open ↗", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: UiSettings,
    onAccentChange: (Long) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onPatternIntensityChange: (Float) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onPreciseLocationChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
) {
    val accents = listOf(0xFF1BA1E2, 0xFF00A300, 0xFFA200FF, 0xFFE671B8, 0xFFF09609, 0xFFE51400)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            MetroTile("accent-settings", Color(0xFF242424), Modifier.fillMaxWidth()) {
                Column {
                    SettingTitle("accent colour", "改變所有重點色和主要磁貼")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        accents.forEach { value ->
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(Color(value.toULong()))
                                    .clickable { onAccentChange(value) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (settings.accentArgb == value) Text("✓", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        item {
            MetroTile("text-settings", Color(0xFF242424), Modifier.fillMaxWidth()) {
                Column {
                    SettingTitle("text size", "${(settings.textScale * 100).roundToInt()}%")
                    Slider(value = settings.textScale, onValueChange = onTextScaleChange, valueRange = 0.85f..1.5f)
                }
            }
        }
        item {
            MetroTile("pattern-settings", Color(0xFF242424), Modifier.fillMaxWidth()) {
                Column {
                    SettingTitle("geometric pattern", "${(settings.patternIntensity / 0.32f * 100).roundToInt()}% intensity")
                    Slider(value = settings.patternIntensity, onValueChange = onPatternIntensityChange, valueRange = 0f..0.32f)
                }
            }
        }
        item { SettingToggleTile("reduce-motion", "reduce motion", "縮短 Pivot 和展開動畫", settings.reduceMotion, onReduceMotionChange) }
        item { SettingToggleTile("contrast", "high contrast", "提高次要文字對比度", settings.highContrast, onHighContrastChange) }
        item { SettingToggleTile("location", "precise location", "使用精確定位及香港街區解析", settings.preciseLocation, onPreciseLocationChange) }
        item { SettingToggleTile("notifications", "weather notifications", "訂閱香港天文台警告更新", settings.notificationsEnabled, onNotificationsChange) }
        item {
            MetroTile("cache", Color(0xFF242424), Modifier.fillMaxWidth(), onClick = onClearCache) {
                Column {
                    SettingTitle("clear cache", "移除離線天氣資料並重新同步")
                    Text("clear now", color = LocalMetroAccent.current, fontSize = 14.sp)
                }
            }
        }
        item {
            Text(
                "Weather Metro 1.0.0\nWeather: Hong Kong Observatory first\nHourly estimates: Open-Meteo",
                color = LocalMetroSubText.current,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingToggleTile(seed: String, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    MetroTile(seed, Color(0xFF242424), Modifier.fillMaxWidth(), onClick = { onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SettingTitle(title, description) }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingTitle(title: String, description: String) {
    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
    Text(description, color = LocalMetroSubText.current, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
}

private fun alertColor(severity: AlertSeverity) = when (severity) {
    AlertSeverity.URGENT -> Color(0xFFE51400)
    AlertSeverity.WARNING -> Color(0xFFF09609)
    AlertSeverity.ADVISORY -> Color(0xFF339933)
    AlertSeverity.TIP -> Color(0xFFB81B53)
}

private fun hkoWeatherIconUrl(code: Int): String =
    "https://www.hko.gov.hk/images/HKOWxIconOutline/pic$code.png"

private fun formatHkoTime(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Asia/Hong_Kong")).format(instant)
    }.getOrElse {
        value.replace("T", " ").take(16)
    }
}

private fun wmoGlyph(code: Int): String = when (code) {
    0 -> "☀"
    in 1..3 -> "☁"
    in 45..48 -> "≋"
    in 51..67, in 80..82 -> "☂"
    in 95..99 -> "ϟ"
    else -> "☁"
}

private fun Number?.display(suffix: String): String = when (this) {
    null -> "--"
    is Double -> if (this % 1.0 == 0.0) "${roundToInt()}$suffix" else "${"%.1f".format(Locale.US, this)}$suffix"
    else -> "$this$suffix"
}
