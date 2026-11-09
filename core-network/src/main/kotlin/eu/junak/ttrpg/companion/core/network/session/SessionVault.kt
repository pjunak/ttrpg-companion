package eu.junak.ttrpg.companion.core.network.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Holds one session cookie value per site origin (`https://host:443`). */
interface SessionVault {
    fun read(origin: String): String?

    fun write(origin: String, value: String?)
}

/**
 * Keeps session cookies encrypted with an AES-256/GCM key in the Android
 * Keystore; only ciphertext reaches shared preferences. A blob that no longer
 * decrypts (the key was invalidated) reads as signed out. Passwords are never
 * stored.
 */
@Singleton
class EncryptedSessionVault @Inject constructor(
    @ApplicationContext context: Context,
) : SessionVault {

    private val preferences = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    override fun read(origin: String): String? =
        preferences.getString(origin, null)?.let(::decrypt)

    @Synchronized
    override fun write(origin: String, value: String?) {
        val encrypted = value?.let(::encrypt)
        preferences.edit().apply {
            if (encrypted == null) remove(origin) else putString(origin, encrypted)
        }.apply()
    }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext.toByteArray()), Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_LENGTH))
        }
        String(cipher.doFinal(bytes, GCM_IV_LENGTH, bytes.size - GCM_IV_LENGTH))
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "companion_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
