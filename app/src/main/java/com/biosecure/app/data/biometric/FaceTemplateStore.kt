package com.biosecure.app.data.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacena la plantilla facial cifrada con una clave de Android Keystore.
 * Nunca guarda la imagen del rostro ni la plantilla en texto plano.
 */
object FaceTemplateStore {
    private const val TAG = "BioSecureTemplate"
    private const val KEY_ALIAS = "biosecure_face_template_key"
    private const val PREF_NAME = "biosecure_face_templates"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private fun requireContext(): Context =
        currentContext ?: error("FaceTemplateStore no inicializado")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun prefKey(uid: String) = "face_template_$uid"
    private fun updatedKey(uid: String) = "face_updated_$uid"

    /** Fecha local de la última inscripción, para no pisar una copia más nueva. */
    fun localUpdatedAt(uid: String): Long =
        prefs(requireContext()).getLong(updatedKey(uid), 0L)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher {
        val transformation = "${KeyProperties.KEY_ALGORITHM_AES}/" +
            "${KeyProperties.BLOCK_MODE_GCM}/" +
            KeyProperties.ENCRYPTION_PADDING_NONE
        return Cipher.getInstance(transformation).apply {
            if (mode == Cipher.ENCRYPT_MODE) {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            } else {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
        }
    }

    /** Sólo cuenta como inscrita si la plantilla se puede descifrar y tiene 192 dimensiones. */
    fun hasTemplate(uid: String): Boolean {
        if (!prefs(requireContext()).contains(prefKey(uid))) {
            Log.d(TAG, "hasTemplate: no hay entrada para $uid")
            return false
        }
        val template = loadTemplate(uid)
        Log.d(TAG, "hasTemplate: uid=$uid size=${template?.size ?: -1}")
        return template?.size == FaceRecognitionManager.EMBEDDING_SIZE
    }

    fun saveTemplate(
        uid: String,
        embedding: FloatArray,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val plain = ByteArray(embedding.size * 4)
        ByteBuffer.wrap(plain).order(ByteOrder.nativeOrder()).asFloatBuffer().put(embedding)

        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        val payload = iv + encrypted

        prefs(requireContext()).edit()
            .putString(prefKey(uid), Base64.encodeToString(payload, Base64.DEFAULT))
            .putLong(updatedKey(uid), updatedAt)
            .apply()
        Log.d(TAG, "saveTemplate: uid=$uid floats=${embedding.size} payload=${payload.size}")
    }

    /** Serializa la plantilla para enviarla al backend (viaja cifrada por HTTPS). */
    fun toBase64(embedding: FloatArray): String {
        val plain = ByteArray(embedding.size * 4)
        ByteBuffer.wrap(plain).order(ByteOrder.nativeOrder()).asFloatBuffer().put(embedding)
        return Base64.encodeToString(plain, Base64.NO_WRAP)
    }

    fun fromBase64(encoded: String): FloatArray? = try {
        val plain = Base64.decode(encoded, Base64.DEFAULT)
        if (plain.isEmpty() || plain.size % 4 != 0) null else {
            val floats = FloatArray(plain.size / 4)
            ByteBuffer.wrap(plain).order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
            floats
        }
    } catch (_: Exception) {
        null
    }

    /** Guarda una plantilla descargada del backend, validando sus 192 dimensiones. */
    fun saveEncodedTemplate(
        uid: String,
        encoded: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val floats = fromBase64(encoded) ?: return false
        if (floats.size != FaceRecognitionManager.EMBEDDING_SIZE) return false
        saveTemplate(uid, floats, updatedAt)
        return true
    }

    fun loadTemplate(uid: String): FloatArray? {
        val encoded = prefs(requireContext()).getString(prefKey(uid), null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.DEFAULT)
            if (payload.size <= IV_SIZE_BYTES) return null
            val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
            val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
            val plain = cipher(Cipher.DECRYPT_MODE, iv).doFinal(encrypted)

            val floats = FloatArray(plain.size / 4)
            ByteBuffer.wrap(plain).order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
            Log.d(TAG, "loadTemplate: uid=$uid floats=${floats.size}")
            floats
        } catch (e: Exception) {
            Log.e(TAG, "loadTemplate FALLÓ uid=$uid: ${e.javaClass.simpleName}: ${e.message}", e)
            // La clave del Keystore pudo cambiar (p. ej. al reinstalar la APK).
            // Se elimina la copia local para que el repositorio la recupere de Mongo.
            prefs(requireContext()).edit()
                .remove(prefKey(uid))
                .remove(updatedKey(uid))
                .apply()
            null
        }
    }

    fun deleteTemplate(uid: String) {
        prefs(requireContext()).edit()
            .remove(prefKey(uid))
            .remove(updatedKey(uid))
            .apply()
    }

    /** El contexto de aplicación se asigna una vez desde la app. */
    @Volatile
    private var currentContext: Context? = null

    fun init(context: Context) {
        currentContext = context.applicationContext
    }
}
