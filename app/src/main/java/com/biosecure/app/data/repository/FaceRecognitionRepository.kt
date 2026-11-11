package com.biosecure.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.biosecure.app.data.face.FaceEmbedder
import com.biosecure.app.data.model.FaceEmbedding
import com.biosecure.app.data.prefs.FaceEnrollmentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.sqrt

/**
 * Resultado de comparar una plantilla en vivo contra la plantilla guardada.
 * Incluye la plantilla en vivo para que el ViewModel pueda reutilizarla
 * durante el mismo marcaje.
 */
data class FaceVerification(
    val live: FaceEmbedding,
    val matched: Boolean,
    val similarity: Float,
    val threshold: Double
)

/**
 * Resultado de la calibración local.
 *
 * [threshold] es el umbral de verificación y se incluye para mostrarlo en
 * pantalla.
 */
data class FaceCalibrationResult(
    val score: Double,
    val threshold: Double,
    val model: String,
    val dim: Int
)

/**
 * Orquesta el flujo facial del cliente:
 *
 *   bitmap alineado -> FaceEmbedder -> plantilla matemática
 *
 * Desde la migración a arquitectura 100 % local (sin Cloud Functions), el
 * cliente también decide si la cara coincide: carga la plantilla de
 * `users/{uid}/faceEmbedding/current`, calcula la similitud coseno y la
 * compara con [FACE_MATCH_THRESHOLD] (0.70).
 *
 * El código servidor de `functions/src/faceVerification.ts` se conserva sin
 * desplegar, pendiente de Blaze, como referencia/futuro.
 */
class FaceRecognitionRepository(
    private val context: Context,
    private val embeddingRepository: FaceEmbeddingRepository = FaceEmbeddingRepository(),
    private val enrollmentStore: FaceEnrollmentStore = FaceEnrollmentStore(context)
) : Closeable {

    companion object {
        /**
         * Umbral de similitud coseno. Debe coincidir con
         * FACE_MATCH_THRESHOLD de functions/src/faceVerification.ts.
         *
         * No bajar de 0.65: por debajo de ese punto dos personas distintas
         * pueden superar el umbral con FaceNet.
         */
        const val FACE_MATCH_THRESHOLD = 0.70
    }

    @Volatile
    private var embedder: FaceEmbedder? = null

    private fun embedder(): FaceEmbedder = synchronized(this) {
        embedder ?: FaceEmbedder(context).also { embedder = it }
    }

    val modelId: String get() = FaceEmbedder.MODEL_ID

    /** Genera una plantilla normalizada L2 a partir de una cara alineada. */
    suspend fun generateEmbedding(sample: Bitmap): FaceEmbedding =
        withContext(Dispatchers.Default) {
            val vector = embedder().embed(sample)
            FaceEmbedding(
                model = FaceEmbedder.MODEL_ID,
                dim = vector.size,
                vectors = listOf(vector.map { it.toDouble() }),
                updatedAt = System.currentTimeMillis()
            )
        }

    /**
     * Genera una plantilla por muestra, las sube a Firestore y guarda una
     * caché local (JPEGs + marca de enrolamiento).
     */
    suspend fun enrollFace(uid: String, samples: List<Bitmap>): Result<FaceEmbedding> {
        return try {
            require(uid.isNotBlank()) { "UID vacío" }
            val usable = samples.take(5)
            require(usable.isNotEmpty()) { "No hay muestras faciales" }

            val generated = usable.map { generateEmbedding(it) }
            val dim = generated.first().dim
            require(generated.all { it.dim == dim }) {
                "Las muestras faciales tienen dimensiones distintas"
            }

            val embedding = FaceEmbedding(
                model = FaceEmbedder.MODEL_ID,
                dim = dim,
                vectors = generated.flatMap { it.vectors },
                updatedAt = System.currentTimeMillis()
            )

            Log.d(
                "FaceEnroll",
                "saving uid=$uid model=${embedding.model} dim=$dim " +
                    "vectors=${embedding.vectors.size}x${embedding.vectors.firstOrNull()?.size}"
            )
            embeddingRepository.save(uid, embedding)

            withContext(Dispatchers.IO) {
                enrollmentStore.save(uid, usable)
                enrollmentStore.saveEmbeddingMarker(uid, embedding.model, embedding.dim)
            }

            Result.success(embedding)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FaceEnroll", "enrollFace failed for uid=$uid", e)
            Result.failure(e)
        }
    }

    /** Genera la plantilla en vivo. No la compara con nada. */
    suspend fun generateLiveEmbedding(sample: Bitmap): Result<FaceEmbedding> {
        return try {
            Result.success(generateEmbedding(sample))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Consulta la plantilla remota. Propaga errores de red/permisos. */
    suspend fun loadRemote(uid: String): FaceEmbedding? = embeddingRepository.loadOrThrow(uid)

    /**
     * Verificación facial local: genera la plantilla en vivo, la compara
     * contra la plantilla guardada y decide si la cara coincide.
     *
     * `matched` es true si la mejor similitud coseno es >= 0.70.
     *
     * Devuelve Result.failure con un código estable cuando no se puede
     * verificar (FACE_NOT_ENROLLED, FACE_MODEL_MISMATCH,
     * FACE_EMBEDDING_INVALID, FACE_VERIFICATION_FAILED, ...).
     */
    suspend fun verify(uid: String, sample: Bitmap): Result<FaceVerification> {
        return try {
            val live = generateEmbedding(sample)
            val comparison = compareWithStored(uid, live)
            Result.success(
                FaceVerification(
                    live = live,
                    matched = comparison.score >= FACE_MATCH_THRESHOLD,
                    similarity = comparison.score.toFloat(),
                    threshold = FACE_MATCH_THRESHOLD
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compara una plantilla en vivo contra la plantilla remota y devuelve la
     * mejor similitud coseno. Se conserva para el modo de depuración.
     *
     * Lanza IllegalStateException con un código estable
     * (FACE_NOT_ENROLLED, FACE_MODEL_MISMATCH, FACE_EMBEDDING_INVALID...).
     */
    suspend fun calibrate(uid: String, live: FaceEmbedding): FaceCalibrationResult =
        compareWithStored(uid, live)

    private suspend fun compareWithStored(
        uid: String,
        live: FaceEmbedding
    ): FaceCalibrationResult {
        require(uid.isNotBlank()) { "UNAUTHENTICATED" }

        val liveVector = live.vectors.firstOrNull()
            ?: throw IllegalStateException("FACE_EMBEDDING_INVALID")
        if (liveVector.isEmpty() || liveVector.size != live.dim) {
            throw IllegalStateException("FACE_EMBEDDING_INVALID")
        }

        val stored = embeddingRepository.loadOrThrow(uid)
            ?: throw IllegalStateException("FACE_NOT_ENROLLED")

        if (stored.model != live.model || stored.dim != live.dim) {
            throw IllegalStateException("FACE_MODEL_MISMATCH")
        }

        val validVectors = stored.vectors.filter { vector ->
            vector.size == stored.dim && vector.all { it.isFinite() }
        }
        if (validVectors.isEmpty()) {
            throw IllegalStateException("FACE_NOT_ENROLLED")
        }

        val best = validVectors.maxOf { cosineSimilarity(liveVector, it) }
        if (!best.isFinite() || best < 0.0) {
            throw IllegalStateException("FACE_VERIFICATION_FAILED")
        }

        return FaceCalibrationResult(
            score = best,
            threshold = FACE_MATCH_THRESHOLD,
            model = stored.model,
            dim = stored.dim
        )
    }

    /** Similitud coseno real; misma fórmula que functions/src/faceVerification.ts. */
    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return -1.0

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val va = a[i]
            val vb = b[i]
            if (!va.isFinite() || !vb.isFinite()) return -1.0
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        if (normA == 0.0 || normB == 0.0) return -1.0
        return dot / sqrt(normA * normB)
    }

    fun isLocallyEnrolled(uid: String): Boolean = enrollmentStore.isEnrolled(uid)

    suspend fun deleteFaceEnrollment(uid: String) {
        withContext(Dispatchers.IO) {
            try {
                embeddingRepository.delete(uid)
            } finally {
                enrollmentStore.clear(uid)
            }
        }
    }

    override fun close() {
        synchronized(this) {
            runCatching { embedder?.close() }
            embedder = null
        }
    }
}
