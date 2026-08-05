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
        // Set to true to save ONE debug snapshot of the exact letterboxed
        // bitmap fed into the interpreter, so we can visually confirm
        // whether preprocessing is producing a normal-looking image or
        // something corrupted. File is saved to the app's external files
        // dir (no permission needed), pull it via Android Studio's Device
        // Explorer at: Android/data/com.mohan.alarm/files/debug_input.png
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
        Log.d("YoloDetector", "detect() called, thread=${Thread.currentThread().name}")
        try {
            val interpreter = interpreter ?: run {
                Log.w("YoloDetector", "detect() bailing early: interpreter is null")
                imageProxy.close()
                return
            }

            // 1. Convert ImageProxy to Bitmap safely
            // NOTE: we deliberately do NOT allocate a second rotated Bitmap here.
            // The previous version called Bitmap.createBitmap(...) on every frame
            // without ever recycling either bitmap, which allocates ~2 full-res
            // bitmaps per frame and exhausts the heap (OutOfMemoryError) within
            // roughly a second of continuous analysis. That crash happens on a
            // background thread and (since OutOfMemoryError is an Error, not an
            // Exception) was NOT caught by `catch (e: Exception)` below, so the
            // ImageProxy for that frame was never closed. With
            // STRATEGY_KEEP_ONLY_LATEST, CameraX will not deliver any further
            // frames to the analyzer until the current one is closed -> the
            // analyzer stalls forever after the first frame, while the separate
            // Preview use case keeps rendering normally.
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // 2. Rotate first (cheap: TFLite's Rot90Op works directly on the
            // TensorImage pipeline, no extra Bitmap allocation needed here).
            var workingBitmap = bitmap
            if (rotationDegrees != 0) {
                val rotateOnly = ImageProcessor.Builder()
                    .add(Rot90Op(-rotationDegrees / 90))
                    .build()
                workingBitmap = rotateOnly.process(TensorImage.fromBitmap(bitmap)).bitmap
            }

            // 3. Letterbox (NOT stretch) into a 640x640 square.
            // IMPORTANT: YOLOv8 is trained on letterboxed images - the source
            // is scaled down preserving its aspect ratio, then padded with
            // neutral gray to fill the remaining square. A plain
            // ResizeOp(640, 640) instead *stretches/squishes* the image to
            // fit, distorting object proportions in a way the model was
            // never trained on. That mismatch alone can push every class's
            // confidence down near zero, which is exactly the "everything
            // detected at 0-9%" symptom we were seeing - the model isn't
            // wrong so much as looking at a warped image.
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
                drawColor(android.graphics.Color.rgb(114, 114, 114)) // YOLO's standard pad color
                val left = ((640 - scaledW) / 2f)
                val top = ((640 - scaledH) / 2f)
                drawBitmap(scaledBitmap, left, top, null)
            }
            scaledBitmap.recycle()

            if (saveDebugFrame) {
                saveDebugFrame = false // only once, so we don't spam storage every frame
                try {
                    val outFile = File(context.getExternalFilesDir(null), "debug_input.png")
                    FileOutputStream(outFile).use { out ->
                        letterboxBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Log.d("YoloDetector", "Saved debug frame to: ${outFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("YoloDetector", "Failed to save debug frame", e)
                }
            }

            val inputTensor = interpreter.getInputTensor(0)
            Log.d(
                "YoloDetector",
                "INPUT tensor shape=${inputTensor.shape().joinToString()} dtype=${inputTensor.dataType()} " +
                    "quantParams=(scale=${inputTensor.quantizationParams().scale}, zeroPoint=${inputTensor.quantizationParams().zeroPoint})"
            )

            // 4. Build the input buffer in the layout THIS model actually
            // wants. The input tensor shape logged above is [1, 3, 640, 640]
            // - that's NCHW (channel-first: all Red values, then all Green,
            // then all Blue), the PyTorch/ONNX convention. But
            // TensorImage/ImageProcessor from tflite-support always produces
            // NHWC (channel-last, R/G/B interleaved per pixel) - the
            // standard TFLite Android convention. The buffer sizes match
            // (3*640*640 either way) so nothing crashes, but every pixel's
            // channel data ends up scrambled to the wrong position, which is
            // exactly why the model was confidently "seeing" things like
            // stop signs and airplanes that weren't there - it was reading
            // structured noise, not your actual photo. Building the NCHW
            // buffer manually here fixes that.
            val pixels = IntArray(640 * 640)
            letterboxBitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640)
            letterboxBitmap.recycle()

            val inputBuffer = java.nio.ByteBuffer
                .allocateDirect(3 * 640 * 640 * 4)
                .order(java.nio.ByteOrder.nativeOrder())
            // Red plane, then Green plane, then Blue plane - each 640*640
            // floats, normalized to 0-1 to match the model's expected range.
            for (channelShift in intArrayOf(16, 8, 0)) { // R, G, B byte offsets within each ARGB int
                for (pixel in pixels) {
                    val value = ((pixel shr channelShift) and 0xFF) / 255f
                    inputBuffer.putFloat(value)
                }
            }
            inputBuffer.rewind()

            // 5. Prepare Output Buffer dynamically
            val outputTensor = interpreter.getOutputTensor(0)
            val shape = outputTensor.shape()
            Log.d("YoloDetector", "output tensor shape=${shape.joinToString()} dtype=${outputTensor.dataType()} numInputs=${interpreter.inputTensorCount} numOutputs=${interpreter.outputTensorCount}")
            val outputBuffer = TensorBuffer.createFixedSize(shape, DataType.FLOAT32)

            // 6. Run AI Inference
            interpreter.run(inputBuffer, outputBuffer.buffer.rewind())

            // 5. Parse Output Matrix
            val outputArray = outputBuffer.floatArray
            val isTransposed = shape[2] == 84
            val numBoxes = if (isTransposed) shape[1] else shape[2]
            val numChannels = if (isTransposed) shape[2] else shape[1]

            var bestConf = -1f
            var bestIdx = -1
            var second = -1f to -1
            var third = -1f to -1
            var cupBest = -1f
            var bottleBest = -1f

            // Loop through all 8400 boxes
            for (box in 0 until numBoxes) {
                // Classes start at channel 4
                for (channel in 4 until numChannels) {
                    val arrayIdx = if (isTransposed) {
                        box * numChannels + channel
                    } else {
                        channel * numBoxes + box
                    }

                    val conf = outputArray[arrayIdx]
                    val clsIdx = channel - 4
                    if (clsIdx == 41 && conf > cupBest) cupBest = conf
                    if (clsIdx == 39 && conf > bottleBest) bottleBest = conf
                    if (conf > bestConf) {
                        third = second
                        second = bestConf to bestIdx
                        bestConf = conf
                        bestIdx = clsIdx
                    }
                }
            }
            fun nm(i: Int) = if (i in labels.indices) labels[i] else "?"
            Log.d(
                "YoloDetector",
                "top1=${nm(bestIdx)}(${"%.1f".format(bestConf * 100)}%) " +
                    "top2=${nm(second.second)}(${"%.1f".format(second.first * 100)}%) " +
                    "top3=${nm(third.second)}(${"%.1f".format(third.first * 100)}%) " +
                    "cup=${"%.1f".format(cupBest * 100)}% bottle=${"%.1f".format(bottleBest * 100)}%"
            )

            // 6. Send Result back to UI
            // Only treat this as a real detection above a meaningful
            // confidence bar. Without this, the UI always displays the
            // single highest-scoring class even when every class scored
            // near zero (e.g. "bench (0%)") - that's not a detection, it's
            // just noise, and showing it as one is misleading and makes
            // debugging harder. 0.35 is a reasonable starting bar; tune to
            // taste once you've confirmed real detections clear it comfortably.
            val CONFIDENCE_THRESHOLD = 0.35f
            if (bestIdx in labels.indices && bestConf >= CONFIDENCE_THRESHOLD) {
                onResult(labels[bestIdx], bestConf)
            } else {
                onResult("Scanning...", 0f)
            }

        } catch (e: Throwable) {
            // Catching Throwable (not just Exception) is intentional and
            // important here: it also catches OutOfMemoryError, which is
            // what was silently killing this analyzer task before and
            // preventing imageProxy.close() from ever running.
            Log.e("YoloDetector", "detect() threw: ${e.javaClass.simpleName}: ${e.message}", e)
            onResult("ERR: ${e.message}", 0f)
        } finally {
            // EXTREMELY IMPORTANT: This is what prevents the camera from freezing!
            // It closes the current frame so Android can send the next lit frame.
            imageProxy.close()
            Log.d("YoloDetector", "detect() finished, imageProxy closed")
        }
    }
}