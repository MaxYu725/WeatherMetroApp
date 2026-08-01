package com.weather.metro.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiSettings(
    val accentArgb: Long = 0xFF1BA1E2,
    val textScale: Float = 1f,
    val patternIntensity: Float = 0.18f,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val preciseLocation: Boolean = true,
    val notificationsEnabled: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setAccent(argb: Long) = edit(KEY_ACCENT, argb)
    fun setTextScale(value: Float) = edit(KEY_TEXT_SCALE, value.coerceIn(0.85f, 1.5f))
    fun setPatternIntensity(value: Float) = edit(KEY_PATTERN, value.coerceIn(0f, 0.32f))
    fun setReduceMotion(value: Boolean) = edit(KEY_REDUCE_MOTION, value)
    fun setHighContrast(value: Boolean) = edit(KEY_HIGH_CONTRAST, value)
    fun setPreciseLocation(value: Boolean) = edit(KEY_PRECISE_LOCATION, value)
    fun setNotificationsEnabled(value: Boolean) = edit(KEY_NOTIFICATIONS, value)

    private fun read() = UiSettings(
        accentArgb = preferences.getLong(KEY_ACCENT, 0xFF1BA1E2),
        textScale = preferences.getFloat(KEY_TEXT_SCALE, 1f),
        patternIntensity = preferences.getFloat(KEY_PATTERN, 0.18f),
        reduceMotion = preferences.getBoolean(KEY_REDUCE_MOTION, false),
        highContrast = preferences.getBoolean(KEY_HIGH_CONTRAST, false),
        preciseLocation = preferences.getBoolean(KEY_PRECISE_LOCATION, true),
        notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS, true),
    )

    private fun edit(key: String, value: Any) {
        preferences.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
            }
        }.apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "weather_metro_settings"
        private const val KEY_ACCENT = "accent"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_PATTERN = "pattern_intensity"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_PRECISE_LOCATION = "precise_location"
        private const val KEY_NOTIFICATIONS = "notifications"

        fun notificationsEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATIONS, true)
    }
}
