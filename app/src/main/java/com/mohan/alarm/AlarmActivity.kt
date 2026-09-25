package com.mohan.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mohan.alarm.ui.theme.AlarmTheme

class AlarmActivity : ComponentActivity() {

    // 1. The Lockdown Flag
    private var isProperlyDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        turnScreenOnAndShowOverLockScreen()
        enableEdgeToEdge()

        // 2. Disable the system Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmActivity, "You must complete the challenge to dismiss!", Toast.LENGTH_SHORT).show()
            }
        })

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Cup"

        // Update / ensure breadcrumbs in SharedPreferences
        val prefs = getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("IS_RINGING", true)
            putInt("ALARM_ID", alarmId)
            putString("TARGET_OBJECT", targetObject)
            putString("RINGTONE_URI", intent.getStringExtra("RINGTONE_URI"))
            putBoolean("VIBRATE", intent.getBooleanExtra("VIBRATE", true))
            apply()
        }

        setContent {
            AlarmTheme {
                AlarmRingScreen(
                    targetObject = targetObject,
                    onAlarmDismissed = {
                        // 3. Mark as properly dismissed BEFORE shutting down
                        isProperlyDismissed = true

                        // Clear the ringing state & saved alarm extras
                        val dismissPrefs = getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
                        dismissPrefs.edit().apply {
                            putBoolean("IS_RINGING", false)
                            remove("ALARM_ID")
                            remove("TARGET_OBJECT")
                            remove("RINGTONE_URI")
                            remove("VIBRATE")
                            apply()
                        }

                        // Stop the AlarmRingService
                        val stopServiceIntent = Intent(this@AlarmActivity, AlarmRingService::class.java).apply {
                            action = AlarmRingService.ACTION_STOP_ALARM
                        }
                        startService(stopServiceIntent)

                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(alarmId)

                        Toast.makeText(this@AlarmActivity, "Match found! Alarm dismissed.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    // 4. Intercept the Home Button and Recent Apps Button
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isProperlyDismissed) {
            relaunchActivity()
        }
    }

    // 5. Intercept swipe-aways from the Recent Apps screen
    override fun onDestroy() {
        // ONLY relaunch if it's not a configuration change (like screen rotation)
        if (!isProperlyDismissed && !isChangingConfigurations) {
            relaunchActivity()
        }
        super.onDestroy()
    }

    // 6. The Boomerang Function
    private fun relaunchActivity() {
        val relaunchIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

            intent.extras?.let { putExtras(it) }
        }
        startActivity(relaunchIntent)
    }

    private fun turnScreenOnAndShowOverLockScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }
}
