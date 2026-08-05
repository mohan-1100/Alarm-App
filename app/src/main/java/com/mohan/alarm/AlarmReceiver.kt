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
        // 1. SHIELD: Ignore rogue OS broadcasts from Xiaomi
        if (intent.action != "com.mohan.alarm.ACTION_TRIGGER_ALARM") {
            return
        }

        // 2. STALE TIME GUARD: If this alarm is firing more than 60 seconds after its
        // scheduled time (e.g. OS memory re-triggering when you open the app), IGNORE IT!
        val expectedTime = intent.getLongExtra("EXPECTED_TRIGGER_TIME", 0L)
        if (expectedTime > 0) {
            val timeDifference = System.currentTimeMillis() - expectedTime
            if (timeDifference > 60_000L) {
                return
            }
        }

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val label = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Cup"

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("TARGET_OBJECT", targetObject)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ALARM_WAKE_CHANNEL"

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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm: $label")
            .setContentText("Scan your $targetObject to dismiss")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(alarmId, notification)
    }
}