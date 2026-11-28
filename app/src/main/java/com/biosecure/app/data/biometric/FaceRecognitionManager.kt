package com.biosecure.app.data.biometric

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter

/**
 * FaceNet/MobileFaceNet en el dispositivo. Devuelve un vector de 192 valores
 * que se compara por distancia euclidiana. No envía imágenes a ningún servidor.
 */
object FaceRecognitionManager {
    private const val TAG = "BioSecureFace"
    private const val MODEL_NAME = "mobilefacenet.tflite"
    const val FACE_SIZE = 112
    const val EMBEDDING_SIZE = 192
    const val MATCH_THRESHOLD = 0.5f

    @Volatile
    private var interpreter: Interpreter? = null
    private val lock = Any()

    val isReady: Boolean
        get() = interpreter != null

    fun initialize(context: Context): Boolean {
        if (interpreter != null) return true
        return synchronized(lock) {
            if (interpreter != null) {
                true
            } else {
                try {
                    val bytes = context.assets.open(MODEL_NAME).use { it.readBytes() }
                    val buffer = ByteBuffer.allocateDirect(bytes.size)
                        .order(ByteOrder.nativeOrder())
                    buffer.put(bytes)
                    buffer.rewind()
                    interpreter = Interpreter(buffer)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo cargar el modelo facial", e)
                    false
                }
            }
        }
    }

    /** Extrae el embedding. Debe llamarse desde un hilo de fondo. */
    fun embed(faceBitmap: Bitmap): FloatArray {
        val model = interpreter ?: throw IllegalStateException("Modelo facial no inicializado")
        val scaled = if (faceBitmap.width == FACE_SIZE && faceBitmap.height == FACE_SIZE) {
            faceBitmap
        } else {
            Bitmap.createScaledBitmap(faceBitmap, FACE_SIZE, FACE_SIZE, true)
        }

        val pixels = IntArray(FACE_SIZE * FACE_SIZE)
        scaled.getPixels(pixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE)

        val input = FloatArray(FACE_SIZE * FACE_SIZE * 3)
        var index = 0
        for (pixel in pixels) {
            input[index++] = (((pixel shr 16) and 0xFF) - 128) / 128f
            input[index++] = (((pixel shr 8) and 0xFF) - 128) / 128f
            input[index++] = ((pixel and 0xFF) - 128) / 128f
        }

        val output = FloatArray(EMBEDDING_SIZE)
        synchronized(lock) {
            val inputTensor = model.getInputTensor(0)
            val outputTensor = model.getOutputTensor(0)
            require(inputTensor.numBytes() == input.size * 4) {
                "Formato de entrada inválido (${inputTensor.numBytes()} bytes)"
            }
            require(outputTensor.numBytes() == output.size * 4) {
                "Formato de salida inválido (${outputTensor.numBytes()} bytes)"
            }

            // Buffers directos: evita el error "Target Tensor hasn't been allocated"
            // que algunos dispositivos lanzan al usar arrays primitivos.
            val inputBuffer = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
            input.forEach { inputBuffer.putFloat(it) }
            inputBuffer.rewind()
            val outputBuffer = ByteBuffer.allocateDirect(output.size * 4).order(ByteOrder.nativeOrder())
            model.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            for (i in output.indices) output[i] = outputBuffer.getFloat()
        }
        return output
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    fun average(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "No hay muestras para promediar" }
        val result = FloatArray(EMBEDDING_SIZE)
        for (embedding in embeddings) {
            require(embedding.size == EMBEDDING_SIZE) { "Tamaño de embedding inválido" }
            for (i in result.indices) result[i] += embedding[i]
        }
        val count = embeddings.size.toFloat()
        for (i in result.indices) result[i] /= count
        return result
    }
}
