package com.jminango.cryptovault.service

import com.jminango.cryptovault.exception.CryptographyException
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
        private const val MASTER_SEED_SALT = "CryptoVault-MasterSeed-Salt-2025"

        // This helper replicates MasterSeedLoader's own CBC encryption (not EncryptionService).
        // Used only by MasterSeedLoaderTest to generate test-encrypted seeds.
        fun encryptForTest(plainText: String, password: String): String {
            val salt = MASTER_SEED_SALT.toByteArray()
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
        assertTrue(encrypted.length > original.length)
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

        assertNotEquals(encrypted1, encrypted2) // random IV per encryption
    }

    @Test
    fun `deve criptografar e descriptografar com AAD corretamente`() {
        val plainText = "42"           // derivation index
        val aad = "user-alice"         // userId bound as AAD

        val encrypted = encryptionService.encrypt(plainText, aad = aad)
        val decrypted = encryptionService.decrypt(encrypted, aad = aad)

        assertEquals(plainText, decrypted)
    }

    @Test
    fun `deve falhar ao descriptografar com AAD incorreto`() {
        val plainText = "42"
        val encrypted = encryptionService.encrypt(plainText, aad = "user-alice")

        // Simulates swapping this ciphertext into user-bob's record
        assertThrows<CryptographyException> {
            encryptionService.decrypt(encrypted, aad = "user-bob")
        }
    }

    @Test
    fun `deve falhar ao descriptografar sem AAD quando criptografado com AAD`() {
        val plainText = "42"
        val encrypted = encryptionService.encrypt(plainText, aad = "user-alice")

        assertThrows<CryptographyException> {
            encryptionService.decrypt(encrypted) // no AAD supplied
        }
    }
}
