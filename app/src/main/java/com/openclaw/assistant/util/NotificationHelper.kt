package com.openclaw.assistant.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.openclaw.assistant.MainActivity
import com.openclaw.assistant.R

object NotificationHelper {

    fun createNotification(context: Context): Notification {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avatar)
            .setContentTitle("OpenClaw Assistant")
            .setContentText("Active – listening & watching")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(Constants.CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            Constants.CHANNEL_ID,
            "OpenClaw Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI assistant foreground service"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
