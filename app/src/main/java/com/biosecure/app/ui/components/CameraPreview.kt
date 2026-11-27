package com.biosecure.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.biosecure.app.data.biometric.FaceRecognitionManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFaceDetected: (Boolean) -> Unit = {},
    onFaceBitmap: (Bitmap?) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastFaceEmit = remember { AtomicLong(0L) }

    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    DisposableEffect(faceDetector, analysisExecutor) {
        onDispose {
            faceDetector.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeImageProxy(
                                imageProxy,
                                faceDetector,
                                onFaceDetected,
                                onFaceBitmap,
                                lastFaceEmit
                            )
                        }
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = modifier.fillMaxSize()
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeImageProxy(
    imageProxy: ImageProxy,
    faceDetector: FaceDetector,
    onFaceDetected: (Boolean) -> Unit,
    onFaceBitmap: (Bitmap?) -> Unit,
    lastFaceEmit: AtomicLong
) {
    try {
        val rotatedBitmap = imageProxy.toRgbaBitmap()?.rotate(imageProxy.imageInfo.rotationDegrees)
        if (rotatedBitmap == null) {
            onFaceDetected(false)
            onFaceBitmap(null)
            imageProxy.close()
            return
        }

        val image = InputImage.fromBitmap(rotatedBitmap, 0)
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                }
                onFaceDetected(face != null)
                if (face == null) {
                    onFaceBitmap(null)
                } else {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFaceEmit.get() >= 200L) {
                        onFaceBitmap(cropFace(rotatedBitmap, face))
                        lastFaceEmit.set(now)
                    }
                }
            }
            .addOnFailureListener {
                onFaceDetected(false)
                onFaceBitmap(null)
            }
            .addOnCompleteListener { imageProxy.close() }
    } catch (_: Exception) {
        imageProxy.close()
    }
}

private fun ImageProxy.toRgbaBitmap(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    val buffer = plane.buffer.duplicate()

    for (y in 0 until h) {
        for (x in 0 until w) {
            val offset = y * plane.rowStride + x * plane.pixelStride
            buffer.position(offset)
            val r = buffer.get().toInt() and 0xFF
            val g = buffer.get().toInt() and 0xFF
            val b = buffer.get().toInt() and 0xFF
            buffer.get() // alpha
            pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun cropFace(bitmap: Bitmap, face: Face): Bitmap {
    val box = face.boundingBox
    val left = (box.left - 10).coerceAtLeast(0)
    val top = (box.top - 10).coerceAtLeast(0)
    val right = (box.right + 10).coerceAtMost(bitmap.width)
    val bottom = (box.bottom + 10).coerceAtMost(bitmap.height)
    val croppedWidth = right - left
    val croppedHeight = bottom - top
    if (croppedWidth <= 0 || croppedHeight <= 0) return bitmap

    val cropped = Bitmap.createBitmap(bitmap, left, top, croppedWidth, croppedHeight)
    val size = minOf(croppedWidth, croppedHeight)
    val square = Bitmap.createBitmap(
        cropped,
        (croppedWidth - size) / 2,
        (croppedHeight - size) / 2,
        size,
        size
    )
    return Bitmap.createScaledBitmap(
        square,
        FaceRecognitionManager.FACE_SIZE,
        FaceRecognitionManager.FACE_SIZE,
        true
    )
}
