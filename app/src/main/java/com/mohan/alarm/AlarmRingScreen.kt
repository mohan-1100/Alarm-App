package com.mohan.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun AlarmRingScreen(
    targetObject: String, // e.g., "bottle" or "cup"
    onAlarmDismissed: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isFlashlightOn by remember { mutableStateOf(false) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var currentDetectedItem by remember { mutableStateOf("Scanning...") }

    var emergencyCountdown by remember { mutableIntStateOf(15) }
    var canEmergencyDismiss by remember { mutableStateOf(false) }
    var hasDismissed by remember { mutableStateOf(false) }
    var showTypingTask by remember { mutableStateOf(false) }

    val quotes = remember {
        listOf(
            "Discipline equals freedom",
            "I am awake and focused today",
            "Every morning is a new beginning",
            "Rise and shine with purpose",
            "Today is full of opportunities",
            "Success starts with waking up",
            "Make today count",
            "Action is the foundational key to success",
            "Great things never come from comfort zones",
            "Believe you can and you are halfway there",
            "Focus on your goals",
            "Win the morning win the day"
        )
    }
    val targetQuote = remember { quotes.random() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        while (emergencyCountdown > 0) {
            delay(1000)
            emergencyCountdown--
        }
        canEmergencyDismiss = true
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val detectorHelper = YoloDetectorHelper(ctx) { label, score ->
                            if (showTypingTask) return@YoloDetectorHelper

                            val percent = (score * 100).toInt()
                            currentDetectedItem = if (label.startsWith("ERR:")) {
                                label // Print exact error message if something fails
                            } else {
                                "Saw: $label ($percent%)"
                            }

                            val cleanDetected = label.trim()
                            val cleanTarget = targetObject.trim()

                            // Dismisses if it matches target with at least 30% confidence
                            if (!hasDismissed && cleanDetected.equals(cleanTarget, ignoreCase = true) && score > 0.30f) {
                                hasDismissed = true

                                // CRITICAL FIX: Force the dismissal action onto the Main UI Thread
                                ContextCompat.getMainExecutor(ctx).execute {
                                    onAlarmDismissed()
                                }
                            }
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            // Forces the camera to output true RGB colors instead of raw YUV
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            // Cap the analysis frame size.
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(640, 640),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .build()
                            )
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    detectorHelper.detect(imageProxy)
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraInstance = cam
                        } catch (e: Exception) {
                            Log.e("Camera", "Binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Camera permission required to scan $targetObject", color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xAA000000)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SCAN YOUR ${targetObject.uppercase()} TO TURN OFF",
                        color = Color(0xFF6BA5FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentDetectedItem,
                        color = if (currentDetectedItem.startsWith("ERR:")) Color(0xFFFF5252) else Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        isFlashlightOn = !isFlashlightOn
                        cameraInstance?.cameraControl?.enableTorch(isFlashlightOn)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
                ) {
                    Text(if (isFlashlightOn) "Flashlight OFF" else "Flashlight ON", color = Color.White)
                }

                Button(
                    onClick = {
                        if (canEmergencyDismiss) {
                            showTypingTask = true
                        } else {
                            Toast.makeText(context, "Please scan object. Emergency unlock in ${emergencyCountdown}s", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canEmergencyDismiss) Color(0xFFFF5252) else Color(0xFF444444)
                    )
                ) {
                    Text(
                        text = if (canEmergencyDismiss) "Type to Unlock" else "Wait (${emergencyCountdown}s)",
                        color = Color.White
                    )
                }
            }
        }

        if (showTypingTask) {
            var typedText by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF1000000))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Type to Unlock",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Type the following quote exactly to dismiss the alarm:",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"$targetQuote\"",
                            color = Color(0xFF6BA5FF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = typedText,
                        onValueChange = {
                            typedText = it
                            errorMessage = ""
                        },
                        label = { Text("Type quote here", color = Color.Gray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            keyboardType = KeyboardType.Password
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6BA5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { showTypingTask = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
                        ) {
                            Text("Back", color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (typedText.trim().equals(targetQuote, ignoreCase = true)) {
                                    if (!hasDismissed) {
                                        hasDismissed = true
                                        onAlarmDismissed()
                                    }
                                } else {
                                    errorMessage = "Incorrect, please try again."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BA5FF))
                        ) {
                            Text("Submit", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
