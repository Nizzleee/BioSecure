package com.biosecure.app.data.repository

import android.content.Context
import android.util.Log
import com.biosecure.app.data.biometric.FaceTemplateStore
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sincroniza la plantilla facial con el backend propio.
 *
 * La copia local sigue cifrada (AES-256-GCM con clave derivada del token).
 * En MongoDB se guarda cifrada con AES-256-GCM usando una clave del servidor.
 * Así, si el celular cambia la clave o se reinstala la app, la plantilla se
 * recupera automáticamente al iniciar sesión y no se vuelve a pedir la inscripción.
 */
class FaceTemplateRepository(context: Context) {
    private val session = SessionStore(context)
    private val api = RetrofitInstance.mongoApi

    suspend fun upload(uid: String, embedding: FloatArray): Boolean = withContext(Dispatchers.IO) {
        if (session.token.isNullOrBlank()) return@withContext false
        try {
            val resp = api.saveFaceTemplate(
                session.authHeader(),
                mapOf("template" to FaceTemplateStore.toBase64(embedding))
            )
            resp.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun downloadAndStore(uid: String): Boolean = withContext(Dispatchers.IO) {
        if (session.token.isNullOrBlank()) return@withContext false
        try {
            val data = api.getFaceTemplate(session.authHeader()).data
            val encoded = data?.template ?: return@withContext false
            FaceTemplateStore.saveEncodedTemplate(uid, encoded, data.updatedAt)
        } catch (_: Exception) {
            false
        }
    }

    /** Devuelve la plantilla local o la recupera del respaldo cifrado en Mongo. */
    suspend fun loadOrRestore(uid: String): FloatArray? = withContext(Dispatchers.IO) {
        FaceTemplateStore.loadTemplate(uid)?.let { return@withContext it }
        if (!downloadAndStore(uid)) return@withContext null
        FaceTemplateStore.loadTemplate(uid)
    }

    /**
     * Mantiene sincronizada la copia local con el respaldo de Mongo.
     * Si el backend ya tiene plantilla, esa es la fuente de verdad; si no,
     * sube la copia local para crearla. Sin red, conserva la copia local.
     */
    suspend fun sync(uid: String): Boolean = withContext(Dispatchers.IO) {
        if (session.token.isNullOrBlank()) {
            return@withContext FaceTemplateStore.hasTemplate(uid)
        }
        try {
            val remote = api.getFaceTemplate(session.authHeader()).data
            val local = FaceTemplateStore.loadTemplate(uid)
            Log.d("BioSecureTemplate", "sync uid=$uid remote=${remote?.template?.length ?: 0} local=${local?.size ?: 0} remoteAt=${remote?.updatedAt ?: 0} localAt=${FaceTemplateStore.localUpdatedAt(uid)}")
            if (remote?.template != null) {
                // Gana la copia más reciente: si la local se inscribió después
                // de la subida (o la subida falló), no se pisa con la vieja.
                if (local == null || remote.updatedAt >= FaceTemplateStore.localUpdatedAt(uid)) {
                    FaceTemplateStore.saveEncodedTemplate(uid, remote.template, remote.updatedAt)
                } else {
                    upload(uid, local)
                }
            } else if (local != null) {
                upload(uid, local)
            }
            FaceTemplateStore.hasTemplate(uid)
        } catch (e: Exception) {
            Log.e("BioSecureTemplate", "sync FALLÓ uid=$uid: ${e.javaClass.simpleName}: ${e.message}", e)
            FaceTemplateStore.hasTemplate(uid)
        }
    }

    suspend fun deleteRemote(): Boolean = withContext(Dispatchers.IO) {
        if (session.token.isNullOrBlank()) return@withContext false
        try {
            api.deleteFaceTemplate(session.authHeader()).ok
        } catch (_: Exception) {
            false
        }
    }
}
