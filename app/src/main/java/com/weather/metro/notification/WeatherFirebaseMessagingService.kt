package com.weather.metro.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.weather.metro.MainActivity
import com.weather.metro.R
import com.weather.metro.data.settings.SettingsRepository

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class WeatherFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)
        if (SettingsRepository.notificationsEnabled(applicationContext)) {
            FirebaseMessaging.getInstance().subscribeToTopic(NotificationChannels.TOPIC_PRODUCTION)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val eventId = data["eventId"] ?: message.messageId ?: System.currentTimeMillis().toString()
        val title = message.notification?.title ?: data["title"] ?: "香港天文台"
        val body = message.notification?.body ?: data["body"] ?: return
        val channel = data["channel"] ?: NotificationChannels.GENERAL
        val target = data["target"] ?: "weathermetro://current"
        val alertId = data["alertId"].orEmpty()
        val notificationKey = alertId.ifBlank { eventId }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = target.toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
                    .setSummaryText("香港天文台官方內容"),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(
                if (channel == NotificationChannels.URGENT) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setGroup("hko_weather_updates")
            .build()

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(notificationKey.hashCode(), notification)
        }
    }
}
