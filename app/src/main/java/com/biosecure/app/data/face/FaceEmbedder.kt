package com.biosecure.app.data.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Convierte una cara alineada en un embedding con facenet.tflite.
 *
 * Modelo: shubham0204/OnDevice-Face-Recognition-Android v0.0.1
 *   - entrada: float32 NHWC [1, 160, 160, 3]
 *   - salida:  float32 [1, 128]
 *   - normalización: estandarización z-score por imagen, igual que el
 *     `StandardizeOp` del proyecto de referencia:
 *         x' = (x - mean) / max(std, 1 / sqrt(N))
 *     donde `x` son los píxeles en [0, 255]. Se calcula sobre los
 *     160*160*3 valores de la imagen.
 *
 * El vector final se normaliza L2 para que el servidor pueda usar el
 * producto punto como similitud coseno.
 */
class FaceEmbedder(context: Context) : Closeable {

    companion object {
        const val MODEL_ID = "facenet-vggface2-128"
        private const val MODEL_ASSET = "models/facenet.tflite"
    }

    private val interpreter = Interpreter(
        loadModel(context),
        Interpreter.Options().apply { numThreads = 2 }
    )

    private val inputTensor = interpreter.getInputTensor(0)
    private val outputTensor = interpreter.getOutputTensor(0)

    private val inputHeight: Int
    private val inputWidth: Int

    /** Dimensión real del embedding (128 para facenet.tflite). */
    val dimension: Int = outputTensor.shape().last()

    init {
        require(inputTensor.dataType() == DataType.FLOAT32) {
            "El modelo facial debe tener entrada FLOAT32 (${MODEL_ASSET})"
        }
        require(outputTensor.dataType() == DataType.FLOAT32) {
            "El modelo facial debe tener salida FLOAT32 (${MODEL_ASSET})"
        }
        val shape = inputTensor.shape()
        require(shape.size == 4 && shape[0] == 1 && shape[3] == 3) {
            "Se esperaba entrada NHWC [1,H,W,3], pero es ${shape.contentToString()}"
        }
        inputHeight = shape[1]
        inputWidth = shape[2]
        require(dimension > 0) { "El modelo facial devolvió una dimensión inválida" }
    }

    /**
     * Genera el embedding de una cara ya alineada. Si el bitmap no tiene el
     * tamaño de entrada del modelo se reescala (bilineal).
     */
    fun embed(face: Bitmap): FloatArray = synchronized(this) {
        require(face.width > 0 && face.height > 0) { "La imagen facial está vacía" }

        val scaled = if (face.width == inputWidth && face.height == inputHeight) {
            face
        } else {
            Bitmap.createScaledBitmap(face, inputWidth, inputHeight, true)
        }

        try {
            val pixelCount = inputWidth * inputHeight
            val pixels = IntArray(pixelCount)
            scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

            // 1) Píxeles RGB en [0,255], como TensorImage del proyecto original.
            val values = FloatArray(pixelCount * 3)
            var index = 0
            var sum = 0.0
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                values[index++] = r
                values[index++] = g
                values[index++] = b
                sum += r + g + b
            }

            // 2) Estandarización z-score por imagen (StandardizeOp).
            val count = values.size
            val mean = (sum / count).toFloat()
            var variance = 0.0
            for (value in values) {
                val diff = value - mean
                variance += diff * diff
            }
            val std = max(
                sqrt(variance / count).toFloat(),
                1f / sqrt(count.toFloat())
            )

            val input = ByteBuffer.allocateDirect(inputTensor.numBytes())
                .order(ByteOrder.nativeOrder())
            for (value in values) {
                input.putFloat((value - mean) / std)
            }
            input.rewind()

            val output = ByteBuffer.allocateDirect(outputTensor.numBytes())
                .order(ByteOrder.nativeOrder())
            interpreter.run(input, output)
            output.rewind()

            val floats = output.asFloatBuffer()
            val vector = FloatArray(floats.remaining()).also { floats.get(it) }
            l2Normalize(vector)
        } finally {
            if (scaled !== face) {
                scaled.recycle()
            }
        }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var normSq = 0f
        for (value in vector) {
            normSq += value * value
        }
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vector.indices) {
            vector[i] /= norm
        }
        return vector
    }

    private fun loadModel(context: Context): ByteBuffer {
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
    }

    override fun close() = interpreter.close()
}
