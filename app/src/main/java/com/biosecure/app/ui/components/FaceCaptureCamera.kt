package com.biosecure.app.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biosecure.app.data.face.FaceAligner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/**
 * Calidad de un fotograma de cámara para enrolamiento / marcaje facial.
 */
data class FaceQuality(
    val faceCount: Int = 0,
    val faceWidthRatio: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val eyesOpen: Boolean = true,
    val centered: Boolean = false,
    val level: QualityLevel = QualityLevel.NO_FACE
) {
    val singleFace: Boolean get() = faceCount == 1
    val sharpEnough: Boolean get() = faceWidthRatio >= MIN_FACE_RATIO

    companion object {
        const val MIN_FACE_RATIO = 0.22f
    }
}

enum class QualityLevel {
    NO_FACE,
    MULTIPLE_FACES,
    TOO_FAR,
    OFF_CENTER,
    EYES_CLOSED,
    TILTED,
    GOOD
}

/**
 * Cámara frontal con detección facial en vivo.
 *
 * @param captureRequest incrementar este contador solicita una captura.
 * @param onQuality se emite en cada análisis con el estado del rostro.
 * @param onCaptured entrega la cara alineada (160x160) lista para FaceEmbedder.
 */
@Composable
fun FaceCaptureCamera(
    modifier: Modifier = Modifier,
    captureRequest: Int = 0,
    onQuality: (FaceQuality) -> Unit = {},
    onCaptured: (Bitmap) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val currentOnQuality by rememberUpdatedState(onQuality)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    val currentRequest by rememberUpdatedState(captureRequest)

    val handledRequest = remember { AtomicInteger(0) }
    val lastAnalysisAt = remember { AtomicLong(0) }

    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                // FaceAligner necesita ojos y nariz; sin esto getLandmark() devuelve null.
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { faceDetector.close() }
            runCatching { analysisExecutor.shutdown() }
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = try {
                    cameraProviderFuture.get()
                } catch (e: Exception) {
                    mainExecutor.execute {
                        currentOnError("No se pudo iniciar la cámara de este dispositivo")
                    }
                    return@addListener
                }

                // Preferimos la cámara frontal. Algunos equipos (y emuladores
                // sin cámara frontal configurada) solo reportan la trasera:
                // en ese caso seguimos con la disponible para no dejar la
                // pantalla en blanco.
                fun selectorFor(lensFacing: Int): CameraSelector? = try {
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    if (cameraProvider.hasCamera(selector)) selector else null
                } catch (e: Exception) {
                    null
                }

                val cameraSelector = selectorFor(CameraSelector.LENS_FACING_FRONT)
                    ?: selectorFor(CameraSelector.LENS_FACING_BACK)
                    ?: run {
                        mainExecutor.execute {
                            currentOnError("Este dispositivo no tiene una cámara disponible")
                        }
                        return@addListener
                    }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                fun analyze(proxy: ImageProxy) {
                    val request = currentRequest
                    val pendingCapture = request > handledRequest.get()
                    val now = SystemClock.elapsedRealtime()

                    // Limitamos el análisis a ~4 fps (y no lo saltamos si hay
                    // una captura pendiente) para no calentar el teléfono.
                    if (!pendingCapture && now - lastAnalysisAt.get() < 250) {
                        proxy.close()
                        return
                    }
                    lastAnalysisAt.set(now)

                    val rotation = proxy.imageInfo.rotationDegrees
                    val raw = try {
                        proxy.toBitmap()
                    } catch (e: Exception) {
                        proxy.close()
                        mainExecutor.execute { currentOnError("No se pudo leer la cámara") }
                        return
                    }
                    proxy.close()

                    val bitmap = rotateAndDownscale(raw, rotation, MAX_ANALYSIS_SIDE)
                    val detector: FaceDetector = faceDetector
                    detector.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { faces ->
                            val quality = evaluateQuality(faces, bitmap.width, bitmap.height)
                            mainExecutor.execute { currentOnQuality(quality) }

                            if (request > handledRequest.getAndSet(request)) {
                                if (quality.singleFace) {
                                    val aligned = FaceAligner.align(bitmap, faces.first())
                                    if (aligned != null) {
                                        mainExecutor.execute { currentOnCaptured(aligned) }
                                    } else {
                                        mainExecutor.execute {
                                            currentOnError(
                                                "No se pudieron detectar los ojos y la nariz. " +
                                                    "Mira de frente con buena luz e inténtalo de nuevo."
                                            )
                                        }
                                    }
                                } else {
                                    mainExecutor.execute {
                                        currentOnError("Mantén un solo rostro dentro del óvalo")
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { error ->
                            mainExecutor.execute {
                                currentOnError(error.message ?: "Error al analizar la imagen")
                            }
                        }
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(720, 960))
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    mainExecutor.execute {
                        currentOnError("No se pudo iniciar la cámara")
                    }
                }
            }, mainExecutor)
            previewView
        },
        modifier = modifier
    )
}

private const val MAX_ANALYSIS_SIDE = 720

private fun rotateAndDownscale(bitmap: Bitmap, rotationDegrees: Int, maxSide: Int): Bitmap {
    val rotated = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
    val longest = max(rotated.width, rotated.height)
    if (longest <= maxSide) return rotated
    val scale = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(
        rotated,
        (rotated.width * scale).toInt().coerceAtLeast(1),
        (rotated.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun evaluateQuality(faces: List<Face>, width: Int, height: Int): FaceQuality {
    if (faces.isEmpty()) return FaceQuality(level = QualityLevel.NO_FACE)
    if (faces.size > 1) {
        return FaceQuality(faceCount = faces.size, level = QualityLevel.MULTIPLE_FACES)
    }

    val face = faces.first()
    val box = face.boundingBox
    val ratio = box.width().toFloat() / width.toFloat()
    val centerX = box.exactCenterX() / width.toFloat()
    val centerY = box.exactCenterY() / height.toFloat()
    val centered = centerX in 0.30f..0.70f && centerY in 0.22f..0.78f
    val eyesOpen = (face.leftEyeOpenProbability ?: 1f) > 0.35f &&
        (face.rightEyeOpenProbability ?: 1f) > 0.35f
    val roll = face.headEulerAngleZ

    val level = when {
        ratio < FaceQuality.MIN_FACE_RATIO -> QualityLevel.TOO_FAR
        !centered -> QualityLevel.OFF_CENTER
        !eyesOpen -> QualityLevel.EYES_CLOSED
        abs(roll) > 18f -> QualityLevel.TILTED
        else -> QualityLevel.GOOD
    }

    return FaceQuality(
        faceCount = 1,
        faceWidthRatio = ratio,
        yaw = face.headEulerAngleY,
        roll = roll,
        eyesOpen = eyesOpen,
        centered = centered,
        level = level
    )
}

