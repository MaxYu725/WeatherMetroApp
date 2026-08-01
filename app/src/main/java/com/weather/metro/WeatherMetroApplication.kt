package com.weather.metro

import android.app.Application
import com.weather.metro.notification.NotificationChannels

class WeatherMetroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}
