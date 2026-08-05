package com.mohan.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mohan.alarm.ui.theme.AlarmTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        turnScreenOnAndShowOverLockScreen()
        enableEdgeToEdge()

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "cup"

        setContent {
            AlarmTheme {
                AlarmRingScreen(
                    targetObject = targetObject,
                    onAlarmDismissed = {
                        // 1. Simply cancel the active notification from the status bar
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.cancel(alarmId)

                        // 2. DO NOT disable the alarm in preferences! It stays enabled for tomorrow.
                        Toast.makeText(this@AlarmActivity, "Match found! Alarm dismissed.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun turnScreenOnAndShowOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}