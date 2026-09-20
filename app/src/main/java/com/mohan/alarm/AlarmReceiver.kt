package com.mohan.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.mohan.alarm.ACTION_TRIGGER_ALARM") {
            return
        }

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
        val ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        val vibrate = intent.getBooleanExtra("VIBRATE", true)

        // 1. Acquire WakeLock to prevent CPU from going back to sleep immediately
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AlarmApp:AlarmWakeLock"
        )
        wakeLock.acquire(10_000L) // Hold wake lock for 10 seconds

        // 2. Build the intent for AlarmActivity
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("TARGET_OBJECT", targetObject)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", vibrate)
        }

        // 3. Directly start the activity from background
        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Fallback Full-Screen Notification
        val pendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ALARM_WAKE_CHANNEL_V2"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Wake Up",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Forces the screen on when an alarm triggers"
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm: $label")
            .setContentText("Scan your $targetObject to dismiss")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(alarmId, notification)
    }
}