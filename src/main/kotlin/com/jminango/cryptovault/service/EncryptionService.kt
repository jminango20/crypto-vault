package com.jminango.cryptovault.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Serviço de criptografia geral (AES)
 */
@Service
class EncryptionService(
    @Value("\${cryptovault.encryption.password}") private val encryptionPassword: String
) {

    companion object {
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGORITHM = "AES"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 16
        private const val SALT = "CryptoVault-Data-Encryption-Salt-2025"
    }

    private val encryptionKey: ByteArray by lazy {
        deriveEncryptionKey()
    }

    /**
     * Criptografa texto
     */
    fun encrypt(plainText: String): String {
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val keySpec = SecretKeySpec(encryptionKey, KEY_ALGORITHM)

            // Gerar IV aleatório
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray())

            // Combinar IV + dados criptografados
            val combined = iv + encrypted

            return Base64.getEncoder().encodeToString(combined)

        } catch (e: Exception) {
            logger.error(e) { "Encryption failed" }
            throw RuntimeException("Failed to encrypt data", e)
        }
    }

    /**
     * Descriptografa texto
     */
    fun decrypt(encryptedText: String): String {
        try {
            val combined = Base64.getDecoder().decode(encryptedText)

            // Separar IV e dados
            val iv = combined.sliceArray(0 until IV_LENGTH)
            val encrypted = combined.sliceArray(IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val keySpec = SecretKeySpec(encryptionKey, KEY_ALGORITHM)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(encrypted)

            return String(decrypted)

        } catch (e: Exception) {
            logger.error(e) { "Decryption failed" }
            throw RuntimeException("Failed to decrypt data", e)
        }
    }

    /**
     * Deriva chave de criptografia do password
     */
    private fun deriveEncryptionKey(): ByteArray {
        val spec = PBEKeySpec(
            encryptionPassword.toCharArray(),
            SALT.toByteArray(),
            ITERATIONS,
            KEY_LENGTH
        )

        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec).encoded

        logger.debug { "Encryption key derived (${key.size} bytes)" }
        return key
    }
}