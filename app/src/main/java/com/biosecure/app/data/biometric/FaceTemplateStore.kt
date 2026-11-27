package com.biosecure.app.data.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

    fun hasTemplate(uid: String): Boolean = prefs(requireContext()).contains(prefKey(uid))

    fun saveTemplate(uid: String, embedding: FloatArray) {
        val plain = ByteArray(embedding.size * 4)
        ByteBuffer.wrap(plain).order(ByteOrder.nativeOrder()).asFloatBuffer().put(embedding)

        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        val payload = iv + encrypted

        prefs(requireContext()).edit()
            .putString(prefKey(uid), Base64.encodeToString(payload, Base64.DEFAULT))
            .apply()
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
            floats
        } catch (_: Exception) {
            null
        }
    }

    fun deleteTemplate(uid: String) {
        prefs(requireContext()).edit().remove(prefKey(uid)).apply()
    }

    /** El contexto de aplicación se asigna una vez desde la app. */
    @Volatile
    private var currentContext: Context? = null

    fun init(context: Context) {
        currentContext = context.applicationContext
    }
}
