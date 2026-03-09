package com.jminango.cryptovault.service

import com.jminango.cryptovault.exception.CryptographyException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Encrypts derivation metadata using AES-256-GCM.
 *
 * The userId is bound as AAD (Additional Authenticated Data), so swapping the
 * encrypted derivation path between user records causes decryption to fail —
 * the 128-bit GCM authentication tag covers both the ciphertext and the AAD.
 */
@Service
class EncryptionService(
    @Value("\${cryptovault.encryption.password}") private val encryptionPassword: String
) {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val GCM_IV_LENGTH = 12  // 96 bits — NIST recommended for GCM
        private const val GCM_TAG_BITS = 128  // 128-bit authentication tag
        private const val SALT = "CryptoVault-Data-Encryption-Salt-2025"
    }

    private val encryptionKey: ByteArray by lazy { deriveEncryptionKey() }

    /**
     * Encrypts [plainText] with AES-256-GCM.
     *
     * @param aad Optional Additional Authenticated Data (e.g. userId). Decryption
     *            will fail unless the same AAD is supplied, preventing substitution
     *            attacks where an attacker copies an encrypted index between records.
     */
    fun encrypt(plainText: String, aad: String? = null): String {
        require(plainText.isNotEmpty()) { "Cannot encrypt empty text" }

        try {
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(encryptionKey, KEY_ALGORITHM),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            aad?.let { cipher.updateAAD(it.toByteArray()) }

            // GCM appends the 16-byte auth tag to the ciphertext output automatically
            val encrypted = cipher.doFinal(plainText.toByteArray())
            return Base64.getEncoder().encodeToString(iv + encrypted)

        } catch (e: Exception) {
            logger.error(e) { "Encryption failed" }
            throw RuntimeException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypts ciphertext produced by [encrypt].
     *
     * @param aad Must match the AAD used during encryption. Throws [CryptographyException]
     *            if the authentication tag is invalid or the AAD does not match —
     *            this detects both tampering and substitution attacks.
     */
    fun decrypt(encryptedText: String, aad: String? = null): String {
        require(encryptedText.isNotEmpty()) { "Cannot decrypt empty text" }

        try {
            val combined = Base64.getDecoder().decode(encryptedText)
            require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted data: too short" }

            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encrypted = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(encryptionKey, KEY_ALGORITHM),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            aad?.let { cipher.updateAAD(it.toByteArray()) }

            return String(cipher.doFinal(encrypted))

        } catch (e: AEADBadTagException) {
            logger.error { "GCM authentication tag verification failed — tampering detected or wrong AAD" }
            throw CryptographyException("Integrity check failed: ciphertext may have been tampered with")
        } catch (e: Exception) {
            logger.error(e) { "Decryption failed" }
            throw RuntimeException("Failed to decrypt data", e)
        }
    }

    private fun deriveEncryptionKey(): ByteArray {
        val spec = PBEKeySpec(encryptionPassword.toCharArray(), SALT.toByteArray(), ITERATIONS, KEY_LENGTH)
        val key = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        logger.debug { "Encryption key derived (${key.size} bytes)" }
        return key
    }
}
