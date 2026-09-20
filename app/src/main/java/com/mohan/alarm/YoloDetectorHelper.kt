package com.mohan.alarm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class YoloDetectorHelper(
    val context: Context,
    val onResult: (String, Float) -> Unit
) {
    companion object {
        @Volatile var saveDebugFrame = false
    }

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    init {
        setupYolo()
    }

    private fun setupYolo() {
        try {
            val model = FileUtil.loadMappedFile(context, "yolov8n.tflite")
            val options = Interpreter.Options().apply { numThreads = 4 }
            interpreter = Interpreter(model, options)
            labels = FileUtil.loadLabels(context, "labels.txt")
        } catch (e: Exception) {
            e.printStackTrace()
            onResult("ERR: Model Load Failed", 0f)
        }
    }

    fun detect(imageProxy: ImageProxy) {
        try {
            val interpreter = interpreter ?: run {
                imageProxy.close()
                return
            }

            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            var workingBitmap = bitmap
            if (rotationDegrees != 0) {
                val rotateOnly = ImageProcessor.Builder()
                    .add(Rot90Op(-rotationDegrees / 90))
                    .build()
                workingBitmap = rotateOnly.process(TensorImage.fromBitmap(bitmap)).bitmap
            }

            val srcW = workingBitmap.width
            val srcH = workingBitmap.height
            val scale = minOf(640f / srcW, 640f / srcH)
            val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
            val scaledH = (srcH * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = Bitmap.createScaledBitmap(workingBitmap, scaledW, scaledH, true)
            if (workingBitmap !== bitmap) workingBitmap.recycle()
            bitmap.recycle()

            val letterboxBitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(letterboxBitmap).apply {
                drawColor(android.graphics.Color.rgb(114, 114, 114))
                val left = ((640 - scaledW) / 2f)
                val top = ((640 - scaledH) / 2f)
                drawBitmap(scaledBitmap, left, top, null)
            }
            scaledBitmap.recycle()

            if (saveDebugFrame) {
                saveDebugFrame = false
                try {
                    val outFile = File(context.getExternalFilesDir(null), "debug_input.png")
                    FileOutputStream(outFile).use { out ->
                        letterboxBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (e: Exception) {
                    Log.e("YoloDetector", "Failed to save debug frame", e)
                }
            }

            val pixels = IntArray(640 * 640)
            letterboxBitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640)
            letterboxBitmap.recycle()

            val inputBuffer = java.nio.ByteBuffer
                .allocateDirect(3 * 640 * 640 * 4)
                .order(java.nio.ByteOrder.nativeOrder())

            for (channelShift in intArrayOf(16, 8, 0)) {
                for (pixel in pixels) {
                    val value = ((pixel shr channelShift) and 0xFF) / 255f
                    inputBuffer.putFloat(value)
                }
            }
            inputBuffer.rewind()

            val outputTensor = interpreter.getOutputTensor(0)
            val shape = outputTensor.shape()
            val outputBuffer = TensorBuffer.createFixedSize(shape, DataType.FLOAT32)

            interpreter.run(inputBuffer, outputBuffer.buffer.rewind())

            val outputArray = outputBuffer.floatArray
            val isTransposed = shape[2] == 84
            val numBoxes = if (isTransposed) shape[1] else shape[2]
            val numChannels = if (isTransposed) shape[2] else shape[1]

            var bestConf = -1f
            var bestIdx = -1

            for (box in 0 until numBoxes) {
                for (channel in 4 until numChannels) {
                    val arrayIdx = if (isTransposed) {
                        box * numChannels + channel
                    } else {
                        channel * numBoxes + box
                    }

                    val conf = outputArray[arrayIdx]
                    val clsIdx = channel - 4

                    if (conf > bestConf) {
                        bestConf = conf
                        bestIdx = clsIdx
                    }
                }
            }

            fun nm(i: Int) = if (i in labels.indices) labels[i] else "?"

            // Only treat this as a real detection above a meaningful threshold
            val CONFIDENCE_THRESHOLD = 0.35f
            if (bestIdx in labels.indices && bestConf >= CONFIDENCE_THRESHOLD) {
                onResult(labels[bestIdx], bestConf)
            } else {
                onResult("Scanning...", 0f)
            }

        } catch (e: Throwable) {
            Log.e("YoloDetector", "detect() threw: ${e.javaClass.simpleName}: ${e.message}", e)
            onResult("ERR: ${e.message}", 0f)
        } finally {
            imageProxy.close()
        }
    }
}