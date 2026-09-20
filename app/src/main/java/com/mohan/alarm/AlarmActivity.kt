package com.mohan.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mohan.alarm.ui.theme.AlarmTheme
import java.io.IOException

class AlarmActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen, bypass lock screen, and prevent screen dimming
        turnScreenOnAndShowOverLockScreen()
        enableEdgeToEdge()

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "cup"

        // Start sound and vibration feedback
        startAlarmFeedback()

        setContent {
            AlarmTheme {
                AlarmRingScreen(
                    targetObject = targetObject,
                    onAlarmDismissed = {
                        // 1. Stop sound and vibration
                        stopAlarmFeedback()

                        // 2. Cancel the active notification from the status bar
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(alarmId)

                        // 3. Feedback toast and close activity
                        Toast.makeText(this@AlarmActivity, "Match found! Alarm dismissed.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun startAlarmFeedback() {
        try {
            val ringtoneUriString = intent.getStringExtra("RINGTONE_URI")
            var customAudioStarted = false

            if (!ringtoneUriString.isNullOrEmpty()) {
                try {
                    val customUri = Uri.parse(ringtoneUriString)
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(this@AlarmActivity, customUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        start()
                    }
                    customAudioStarted = true
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    mediaPlayer?.release()
                    mediaPlayer = null
                } catch (e: IOException) {
                    e.printStackTrace()
                    mediaPlayer?.release()
                    mediaPlayer = null
                } catch (e: Exception) {
                    e.printStackTrace()
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }

            if (!customAudioStarted) {
                // Audio: Fallback to default alarm tone with loop
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmActivity, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            }

            // Vibration: Only initialize and start the Vibrator if vibrate is true
            val vibrate = intent.getBooleanExtra("VIBRATE", true)
            if (vibrate) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                val pattern = longArrayOf(0, 500, 500) // Vibrate 500ms, pause 500ms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmFeedback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopAlarmFeedback()
        super.onDestroy()
    }

    private fun turnScreenOnAndShowOverLockScreen() {
        // Ensure screen stays awake during object scanning
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

        // Dismiss the keyguard to allow immediate interaction
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }
}