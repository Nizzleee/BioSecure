package com.biosecure.app.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Clave de Android Keystore protegida por biometría.
 *
 * Se usa como `CryptoObject` del BiometricPrompt: si se autentica, el sistema
 * deja usar la clave una vez. Así no basta con "simular" la respuesta del
 * callback (hook/root): la operación criptográfica solo ocurre si el sensor
 * biométrico validó al usuario de verdad.
 */
object BiometricCrypto {

    private const val KEY_NAME = "biosecure_biometric_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    /** Devuelve un cifrador listo para autenticar, o null si el equipo no lo soporta. */
    fun newCipherOrNull(): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_NAME)) {
                generateKey()
            }
            val key = keyStore.getKey(KEY_NAME, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (e: Exception) {
            // La clave pudo quedar invalidada (p. ej. se enroló otra huella).
            // Se borra para regenerarla en el siguiente intento.
            runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_NAME) }
            null
        }
    }

    fun wrap(cipher: Cipher): BiometricPrompt.CryptoObject =
        BiometricPrompt.CryptoObject(cipher)

    private fun generateKey() {
        val builder = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )
        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }
}
