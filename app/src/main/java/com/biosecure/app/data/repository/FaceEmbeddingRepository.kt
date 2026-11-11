package com.biosecure.app.data.repository

import android.util.Log
import com.biosecure.app.data.model.FaceEmbedding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Lee y escribe la plantilla facial del empleado en
 * users/{uid}/faceEmbedding/current (subcolección, no campo plano).
 *
 * Esta estructura permite reglas de Firestore específicas para datos
 * biométricos, separadas del resto del documento del usuario.
 *
 * Formato de `vectors`: Firestore rechaza arrays de arrays en el SDK de
 * Android, así que cada vector se guarda como un mapa `{"v": [128 doubles]}`.
 * La lectura acepta tanto ese formato como el antiguo array anidado.
 */
class FaceEmbeddingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun ref(uid: String) = db.collection("users")
        .document(uid)
        .collection("faceEmbedding")
        .document("current")

    suspend fun save(uid: String, embedding: FaceEmbedding) {
        try {
            ref(uid).set(
                mapOf(
                    "model" to embedding.model,
                    "dim" to embedding.dim,
                    "vectors" to embedding.vectors.map { vector -> mapOf("v" to vector) },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FaceEmbeddingRepo", "save failed for uid=$uid", e)
            throw e
        }
    }

    /**
     * Igual que [load], pero propaga los errores de red/permisos.
     * Úsalo cuando sea importante distinguir "no enrolado" de "no se pudo
     * consultar": `load()` devuelve null en ambos casos.
     */
    suspend fun loadOrThrow(uid: String): FaceEmbedding? {
        val snap = ref(uid).get(Source.SERVER).await()
        if (!snap.exists()) return null

        val rawVectors = snap.get("vectors") as? List<*> ?: return null
        val vectors = rawVectors.mapNotNull { row ->
            when (row) {
                // Formato nuevo: {"v": [128 doubles]}.
                is Map<*, *> -> (row["v"] as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toDouble() }
                // Compatibilidad con plantillas antiguas guardadas como array
                // anidado (si alguna llegó a existir).
                is List<*> -> row.mapNotNull { (it as? Number)?.toDouble() }
                else -> null
            }
        }
        if (vectors.isEmpty()) return null

        return FaceEmbedding(
            model = snap.getString("model") ?: return null,
            dim = (snap.getLong("dim") ?: 0L).toInt(),
            vectors = vectors,
            updatedAt = snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
        )
    }

    suspend fun load(uid: String): FaceEmbedding? {
        return try {
            loadOrThrow(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun delete(uid: String) {
        try {
            ref(uid).delete().await()
        } catch (e: CancellationException) {
            throw e
        }
    }
}
