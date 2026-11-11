package com.biosecure.app.data.model

/**
 * Plantilla facial de un empleado: una o varias muestras (vectors) tomadas
 * durante el enrolamiento, ya normalizadas L2.
 */
data class FaceEmbedding(
    val model: String,
    val dim: Int,
    val vectors: List<List<Double>>,
    val updatedAt: Long = 0L
)

/** Resultado de comparar una cara en vivo contra la plantilla guardada. */
data class FaceVerifyResult(
    val matched: Boolean,
    val similarity: Float,
    val error: String? = null
)
