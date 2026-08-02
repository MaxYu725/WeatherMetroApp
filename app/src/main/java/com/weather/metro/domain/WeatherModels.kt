package com.weather.metro.domain

data class LocationInfo(
    val latitude: Double = 22.3019,
    val longitude: Double = 114.1742,
    val label: String = "香港天文台",
    val district: String = "油尖旺",
    val stationName: String = "香港天文台",
    val tideStationCode: String = "QUB",
    val accuracyMetres: Int? = null,
)

data class CurrentConditions(
    val temperatureC: Double? = null,
    val feelsLikeC: Double? = null,
    val humidityPercent: Int? = null,
    val minTemperatureC: Double? = null,
    val maxTemperatureC: Double? = null,
    val weatherIconCode: Int? = null,
    val windDirection: String? = null,
    val windSpeedKmh: Int? = null,
    val gustKmh: Int? = null,
    val rainfallMm: Double? = null,
    val uvIndex: Double? = null,
    val uvDescription: String? = null,
    val visibilityKm: Double? = null,
    val pressureHpa: Double? = null,
    val dewPointC: Double? = null,
    val observedAt: String? = null,
)

enum class AlertSeverity {
    URGENT,
    WARNING,
    ADVISORY,
    TIP,
}

data class WeatherAlert(
    val id: String,
    val code: String,
    val title: String,
    val content: String,
    val updatedAt: String,
    val severity: AlertSeverity,
    val iconUrl: String? = null,
    val actionCode: String = "ISSUE",
    val isTip: Boolean = false,
)

data class HourlyWeather(
    val epochMillis: Long,
    val label: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    val precipitationProbability: Int,
    val precipitationMm: Double,
    val weatherCode: Int,
    val windDirection: String,
    val windSpeedKmh: Int,
    val uvIndex: Double,
)

data class DailyForecast(
    val date: String,
    val weekday: String,
    val iconCode: Int,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val minHumidityPercent: Int,
    val maxHumidityPercent: Int,
    val precipitationProbability: String,
    val wind: String,
    val description: String,
)

data class LocalForecast(
    val generalSituation: String = "",
    val forecastPeriod: String = "",
    val forecastDescription: String = "",
    val outlook: String = "",
    val tropicalCycloneInfo: String = "",
    val fireDangerWarning: String = "",
    val updatedAt: String = "",
)

data class NineDayForecast(
    val generalSituation: String = "",
    val days: List<DailyForecast> = emptyList(),
    val updatedAt: String = "",
)

data class TideEvent(
    val time: String,
    val heightMetres: Double?,
    val type: String,
)

data class AstronomyInfo(
    val sunrise: String? = null,
    val solarTransit: String? = null,
    val sunset: String? = null,
    val moonrise: String? = null,
    val moonTransit: String? = null,
    val moonset: String? = null,
    val moonPhase: String? = null,
    val moonIlluminationPercent: Int? = null,
    val tides: List<TideEvent> = emptyList(),
)

data class WeatherSnapshot(
    val location: LocationInfo = LocationInfo(),
    val current: CurrentConditions = CurrentConditions(),
    val alerts: List<WeatherAlert> = emptyList(),
    val hourly: List<HourlyWeather> = emptyList(),
    val localForecast: LocalForecast = LocalForecast(),
    val nineDayForecast: NineDayForecast = NineDayForecast(),
    val astronomy: AstronomyInfo = AstronomyInfo(),
    val fetchedAtEpochMillis: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
)

sealed interface WeatherLoadState {
    data object Loading : WeatherLoadState
    data class Ready(val snapshot: WeatherSnapshot, val refreshing: Boolean = false) : WeatherLoadState
    data class Error(val message: String, val cached: WeatherSnapshot? = null) : WeatherLoadState
}
