package com.biosecure.app.data.prefs

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Caché local del enrolamiento facial.
 *
 * Las muestras JPEG se guardan solo como referencia/miniaturas en el
 * dispositivo. La plantilla matemática que usa el servidor vive en
 * `users/{uid}/faceEmbedding/current` (Firestore), por eso aquí solo se
 * recuerda que el usuario ya completó el enrolamiento y con qué modelo.
 *
 * Cada usuario (uid) tiene sus propios datos, así que un mismo celular
 * puede usarse con varias cuentas sin mezclar rostros.
 */
class FaceEnrollmentStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val baseDir = File(context.filesDir, "face_enrollment").apply { mkdirs() }

    /**
     * Verdadero si hay muestras locales o si se completó un enrolamiento
     * (aunque las miniaturas se hayan borrado). La plantilla remota se
     * consulta aparte con FaceEmbeddingRepository.
     */
    fun isEnrolled(uid: String): Boolean = hasEnrollment(uid)

    fun hasEnrollment(uid: String): Boolean {
        if (uid.isBlank()) return false
        return samplePaths(uid).isNotEmpty() || prefs.getBoolean(keyHasEmbedding(uid), false)
    }

    fun embeddingModel(uid: String): String? = prefs.getString(keyEmbeddingModel(uid), null)

    /** Guarda la marca local tras subir la plantilla a Firestore. */
    fun saveEmbeddingMarker(uid: String, model: String, dim: Int) {
        if (uid.isBlank()) return
        prefs.edit()
            .putBoolean(keyHasEmbedding(uid), true)
            .putString(keyEmbeddingModel(uid), model)
            .putInt(keyEmbeddingDim(uid), dim)
            .putLong(keyEnrolledAt(uid), System.currentTimeMillis())
            .apply()
    }

    fun enrolledAt(uid: String): Long = prefs.getLong(keyEnrolledAt(uid), 0L)

    fun samplePaths(uid: String): List<String> {
        if (uid.isBlank()) return emptyList()
        val raw = prefs.getString(keySamples(uid), null) ?: return emptyList()
        return raw.split("|")
            .filter { it.isNotBlank() }
            .filter { File(it).exists() }
    }

    fun sampleFiles(uid: String): List<File> = samplePaths(uid).map { File(it) }

    /**
     * Reemplaza el enrolamiento del usuario con las muestras indicadas.
     * Devuelve true si todas las imágenes se escribieron correctamente.
     */
    fun save(uid: String, samples: List<Bitmap>): Boolean {
        if (uid.isBlank() || samples.isEmpty()) return false
        return try {
            clear(uid)
            val userDir = File(baseDir, uid).apply { mkdirs() }
            val paths = samples.mapIndexedNotNull { index, bitmap ->
                val file = File(userDir, "face_${index + 1}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                file.absolutePath
            }
            if (paths.isEmpty()) return false
            prefs.edit()
                .putString(keySamples(uid), paths.joinToString("|"))
                .putLong(keyEnrolledAt(uid), System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clear(uid: String) {
        if (uid.isBlank()) return
        samplePaths(uid).forEach { runCatching { File(it).delete() } }
        runCatching { File(baseDir, uid).deleteRecursively() }
        prefs.edit()
            .remove(keySamples(uid))
            .remove(keyEnrolledAt(uid))
            .remove(keyHasEmbedding(uid))
            .remove(keyEmbeddingModel(uid))
            .remove(keyEmbeddingDim(uid))
            .apply()
    }

    private fun keySamples(uid: String) = "$uid.samples"
    private fun keyEnrolledAt(uid: String) = "$uid.enrolledAt"
    private fun keyHasEmbedding(uid: String) = "$uid.hasEmbedding"
    private fun keyEmbeddingModel(uid: String) = "$uid.embeddingModel"
    private fun keyEmbeddingDim(uid: String) = "$uid.embeddingDim"

    companion object {
        private const val PREFS_NAME = "biosecure_face_enrollment"
    }
}
