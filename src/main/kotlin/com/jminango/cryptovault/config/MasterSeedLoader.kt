package com.jminango.cryptovault.config

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Loads the master seed at application startup and exposes it as a Spring bean.
 *
 * Three seed formats are supported, in order of security:
 *   ENC:<base64>   — AES/CBC-encrypted seed (production); decrypted using MASTER_PASSWORD
 *   BASE64:<b64>   — Base64-encoded plaintext (development only)
 *   <plaintext>    — Raw string (development only; triggers a CRITICAL log warning)
 *
 * In production, the seed must be encrypted with [decryptMasterSeed] and stored as
 * ENC:<ciphertext>, or loaded from an HSM via the MASTER_SEED environment variable.
 */
@Configuration
class MasterSeedLoader(
    @Value("\${cryptovault.master.seed}")
    private val masterSeedConfig: String,

    @Value("\${cryptovault.master.password}")
    private val masterPassword: String
) {

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 16
        private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val AES_KEY_ALGORITHM = "AES"
        private const val MASTER_SEED_SALT = "CryptoVault-MasterSeed-Salt-2025"

        private const val PREFIX_ENCRYPTED = "ENC:"
        private const val PREFIX_BASE64 = "BASE64:"

        /**
         * Decrypts a master seed that was encrypted with PBKDF2 + AES/CBC.
         *
         * The ciphertext format is: Base64( IV[16] || AES_CBC(seed) ).
         * The key is derived from [password] via PBKDF2-HMAC-SHA256 at 100,000 iterations.
         */
        @JvmStatic
        fun decryptMasterSeed(encryptedBase64: String, password: String): ByteArray {
            try {
                val encryptedData = Base64.getDecoder().decode(encryptedBase64)
                require(encryptedData.size > IV_LENGTH) { "Encrypted data too short" }

                val keyBytes = deriveDecryptionKey(password)
                val iv = encryptedData.sliceArray(0 until IV_LENGTH)
                val encrypted = encryptedData.sliceArray(IV_LENGTH until encryptedData.size)

                val cipher = Cipher.getInstance(AES_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, AES_KEY_ALGORITHM), IvParameterSpec(iv))
                return cipher.doFinal(encrypted)

            } catch (e: IllegalArgumentException) {
                throw RuntimeException("Invalid encrypted master seed format", e)
            } catch (e: Exception) {
                throw RuntimeException("Failed to decrypt master seed. Verify MASTER_PASSWORD is correct", e)
            }
        }

        private fun deriveDecryptionKey(password: String): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), MASTER_SEED_SALT.toByteArray(), PBKDF2_ITERATIONS, KEY_LENGTH)
            return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        }
    }

    @Bean
    fun masterSeed(): MasterSeed {
        logger.info { "Loading master seed configuration..." }

        val seedBytes = when {
            masterSeedConfig.startsWith(PREFIX_ENCRYPTED) -> {
                logger.info { "Decrypting production master seed..." }
                decryptMasterSeed(masterSeedConfig.removePrefix(PREFIX_ENCRYPTED), masterPassword)
            }

            masterSeedConfig.startsWith(PREFIX_BASE64) -> {
                logger.warn { "Master seed in BASE64 format — use only in development!" }
                Base64.getDecoder().decode(masterSeedConfig.removePrefix(PREFIX_BASE64))
            }

            else -> {
                // Plain-text seeds are rejected in production via environment-specific config.
                logger.error { "CRITICAL: Plain-text master seed detected. NEVER use in production!" }
                masterSeedConfig.toByteArray()
            }
        }

        require(seedBytes.size >= 32) { "Master seed must be at least 32 bytes (256 bits)" }
        logger.info { "Master seed loaded successfully (${seedBytes.size} bytes)" }

        return MasterSeed(seedBytes)
    }
}

/**
 * Immutable wrapper for the master seed byte array.
 *
 * [getBytes] returns a defensive copy so callers cannot mutate the internal state.
 * [toString] is overridden to prevent the seed from appearing in logs.
 */
data class MasterSeed(private val seed: ByteArray) {

    fun getBytes(): ByteArray = seed.clone()

    fun size(): Int = seed.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MasterSeed) return false
        return seed.contentEquals(other.seed)
    }

    override fun hashCode(): Int = seed.contentHashCode()

    override fun toString(): String = "MasterSeed(size=${seed.size} bytes)"
}
