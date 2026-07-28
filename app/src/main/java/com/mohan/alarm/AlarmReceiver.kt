package com.mohan.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Cup"

        // 1. Create the Intent that opens your Ringing Screen
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_LABEL", label)
            putExtra("TARGET_OBJECT", targetObject)
        }

        // 2. Wrap it in a PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ALARM_WAKE_CHANNEL"

        // 3. Create a High-Priority Notification Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Wake Up",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Forces the screen on when an alarm triggers"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 4. Build the Full-Screen Intent Notification to bypass Android's background restrictions
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Default android alarm icon
            .setContentTitle("Alarm: $label")
            .setContentText("Scan your $targetObject to dismiss")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // THIS is what forces the screen on!
            .setAutoCancel(true)
            .build()

        // 5. Fire the notification
        notificationManager.notify(12345, notification)
    }
}