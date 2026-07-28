package com.mohan.alarm

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mohan.alarm.ui.theme.AlarmTheme
import java.util.concurrent.Executors

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        turnScreenOnAndShowOverLockScreen()
        enableEdgeToEdge()

        val alarmLabel = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Cup"

        setContent {
            AlarmTheme {
                var showCamera by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        showCamera = true
                    } else {
                        Toast.makeText(this, "Camera access is required to stop the alarm!", Toast.LENGTH_LONG).show()
                    }
                }

                if (showCamera) {
                    CameraScreen(
                        targetObject = targetObject,
                        onScanSuccess = {
                            // The AI found the object! Turn off the alarm.
                            Toast.makeText(this, "Match found! Alarm dismissed.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    )
                } else {
                    RingingScreen(
                        label = alarmLabel,
                        targetObject = targetObject,
                        onScanClick = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                showCamera = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }
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

@Composable
fun RingingScreen(label: String, targetObject: String, onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "WAKE UP!", color = Color.Red, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(16.dp))

        // Tells the user exactly what to look for
        Text(
            text = "Scan a $targetObject to turn off the alarm.",
            color = Color.LightGray,
            fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onScanClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Text(text = "Open Camera & Scan", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(targetObject: String, onScanSuccess: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var currentPrediction by remember { mutableStateOf("Analyzing...") }

    val objectSynonyms = mapOf(
        "Bottle" to listOf("Bottle", "Water bottle", "Plastic bottle", "Flask", "Thermos", "Glass bottle", "Tableware", "Chair"),
        "Cup" to listOf("Cup", "Mug", "Coffee cup", "Teacup", "Paper cup", "Tableware")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()

                // FIX 1: Lower the AI's default threshold so it gives us more secondary guesses!
                val options = ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.40f)
                    .build()
                val labeler = ImageLabeling.getClient(options)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                                    labeler.process(image)
                                        .addOnSuccessListener { labels ->
                                            if (labels.isNotEmpty()) {
                                                // FIX 2: Print the top 3 guesses and their percentages to the screen!
                                                currentPrediction = labels.take(3).joinToString("\n") {
                                                    "• ${it.text} (${(it.confidence * 100).toInt()}%)"
                                                }

                                                val acceptedWords = objectSynonyms[targetObject] ?: listOf(targetObject)

                                                for (label in labels) {
                                                    val detectedWord = label.text
                                                    val isMatch = acceptedWords.any { detectedWord.contains(it, ignoreCase = true) }

                                                    if (isMatch && label.confidence >= 0.40f) {
                                                        onScanSuccess()
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (exc: Exception) {
                        Toast.makeText(ctx, "Failed to start camera.", Toast.LENGTH_SHORT).show()
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Target: $targetObject",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xBB000000), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // This box will now show a list of what the AI is thinking!
            Text(
                text = currentPrediction,
                color = Color(0xFF8AB4F8),
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Color(0xBB000000), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            )
        }
    }
}