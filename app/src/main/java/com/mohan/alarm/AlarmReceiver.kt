package com.mohan.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

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

        // 2. Start ForegroundService to handle notification, audio, vibration, and activity launch
        val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_START_ALARM
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("TARGET_OBJECT", targetObject)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", vibrate)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
