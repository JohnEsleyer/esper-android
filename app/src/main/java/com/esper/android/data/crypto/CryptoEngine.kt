package com.esper.android.data.crypto

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Byte-for-byte zero-knowledge cryptographic parity with web/crypto.js:
 * Format: "enc:v1:<Base64(IV)>:<Base64(Ciphertext)>"
 * Algorithm: AES/GCM/NoPadding (256-bit key, 12-byte IV)
 * Key Derivation: PBKDF2WithHmacSHA256 (100,000 rounds)
 *
 * Defensive by design: null keys and malformed payloads pass through
 * unchanged (never crash, never destroy data). Double-encrypted values
 * are unwrapped recursively, mirroring web safeDecrypt.
 */
object CryptoEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val DEFAULT_SALT = "esper_user_salt_2025"

    fun deriveKey(userId: String, salt: String = DEFAULT_SALT): SecretKeySpec {
        val spec = PBEKeySpec(userId.toCharArray(), salt.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plaintext: String, key: SecretKeySpec?): String {
        if (key == null || plaintext.startsWith("enc:v1:")) return plaintext
        return try {
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "enc:v1:$ivB64:$dataB64"
        } catch (e: Exception) {
            Log.e("CryptoEngine", "Encryption failed", e)
            plaintext
        }
    }

    fun decrypt(ciphertext: String?, key: SecretKeySpec?): String {
        if (ciphertext == null) return ""
        if (key == null || !ciphertext.startsWith("enc:v1:")) return ciphertext
        return try {
            val parts = ciphertext.split(":")
            if (parts.size < 4) return ciphertext
            val iv = Base64.decode(parts[2], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[3], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(encryptedBytes)
            val decrypted = String(plainBytes, Charsets.UTF_8)
            if (decrypted.startsWith("enc:v1:")) decrypt(decrypted, key) else decrypted
        } catch (e: Exception) {
            Log.e("CryptoEngine", "Decryption failed: ${e.message}")
            ciphertext
        }
    }
}
