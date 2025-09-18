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

@Configuration
class MasterSeedLoader(
    @Value("\${cryptovault.master.seed}") private val masterSeedConfig: String,
    @Value("\${cryptovault.master.password}") private val masterPassword: String
) {

    companion object {
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 16
        private const val MASTER_SEED_SALT = "CryptoVault-MasterSeed-Salt-2025"

        /**
         * Método ESTÁTICO para descriptografar
         */
        @JvmStatic
        fun decryptMasterSeed(encryptedBase64: String, password: String): ByteArray {
            try {
                val encryptedData = Base64.getDecoder().decode(encryptedBase64)

                val salt = MASTER_SEED_SALT.toByteArray()
                val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded

                val iv = encryptedData.sliceArray(0 until IV_LENGTH)
                val encrypted = encryptedData.sliceArray(IV_LENGTH until encryptedData.size)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val keySpec = SecretKeySpec(keyBytes, "AES")
                val ivSpec = IvParameterSpec(iv)

                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                return cipher.doFinal(encrypted)

            } catch (e: Exception) {
                throw RuntimeException("Failed to decrypt master seed. Check MASTER_PASSWORD", e)
            }
        }
    }

    /**
     * Bean do Master Seed - carregado UMA vez no startup
     * Disponível para injeção em outros serviços
     */
    @Bean
    fun masterSeed(): MasterSeed {
        logger.info { "Loading master seed..." }

        val seedBytes = when {
            // Seed criptografado (produção)
            masterSeedConfig.startsWith("ENC:") -> {
                logger.info { "Decrypting master seed..." }
                val encrypted = masterSeedConfig.removePrefix("ENC:")
                decryptMasterSeed(encrypted, masterPassword)
            }

            // Seed em Base64 (desenvolvimento)
            masterSeedConfig.startsWith("BASE64:") -> {
                logger.warn { "Master seed in Base64 (use only in dev!)" }
                Base64.getDecoder().decode(masterSeedConfig.removePrefix("BASE64:"))
            }

            else -> {
                logger.error { "DANGER: Plain text master seed! NEVER use in production!" }
                masterSeedConfig.toByteArray()
            }
        }

        logger.info { "Master seed loaded successfully (${seedBytes.size} bytes)" }
        return MasterSeed(seedBytes)
    }
}

/**
 * Wrapper imutável para o Master Seed
 */
data class MasterSeed(private val seed: ByteArray) {
    fun getBytes(): ByteArray = seed.clone() // Retorna cópia para evitar mutação

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MasterSeed) return false
        return seed.contentEquals(other.seed)
    }

    override fun hashCode(): Int = seed.contentHashCode()
}