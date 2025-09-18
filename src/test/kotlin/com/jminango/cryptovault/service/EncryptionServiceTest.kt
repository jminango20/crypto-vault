package com.jminango.cryptovault.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionServiceTest {

    companion object {
        // ✅ Use o MESMO salt do MasterSeedLoader
        private const val MASTER_SEED_SALT = "CryptoVault-MasterSeed-Salt-2025"

        fun encryptForTest(plainText: String, password: String): String {
            val salt = MASTER_SEED_SALT.toByteArray() // 👈 MESMO SALT!
            val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray())

            val combined = iv + encrypted
            return Base64.getEncoder().encodeToString(combined)
        }
    }

    private val encryptionService = EncryptionService("senha-de-teste-encrypt")

    @Test
    fun `deve criptografar e descriptografar corretamente`() {
        val original = "texto-secreto-para-teste-123"
        val encrypted = encryptionService.encrypt(original)
        val decrypted = encryptionService.decrypt(encrypted)

        assertEquals(original, decrypted)
        assertNotEquals(original, encrypted)
        assertTrue(encrypted.startsWith("ENC:") || encrypted.length > original.length)
    }

    @Test
    fun `deve falhar com senha errada`() {
        val original = "texto-secreto"
        val encrypted = EncryptionService("senha-correta").encrypt(original)

        val wrongService = EncryptionService("senha-errada")

        assertThrows<RuntimeException> {
            wrongService.decrypt(encrypted)
        }
    }

    @Test
    fun `deve gerar criptografias diferentes para mesmo texto`() {
        val text = "mesmo-texto"
        val encrypted1 = encryptionService.encrypt(text)
        val encrypted2 = encryptionService.encrypt(text)

        assertNotEquals(encrypted1, encrypted2) // por causa do IV aleatório
    }
}