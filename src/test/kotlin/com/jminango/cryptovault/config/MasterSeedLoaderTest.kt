package com.jminango.cryptovault.config

import com.jminango.cryptovault.service.EncryptionServiceTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = [
    "cryptovault.master.seed=BASE64:dGVzdC1zZWVkLWZvci10ZXN0aW5n", // "test-seed-for-testing" em Base64
    "cryptovault.master.password=test-password"
])
class MasterSeedLoaderTest {

    @Value("\${cryptovault.master.seed}")
    private lateinit var masterSeedConfig: String

    @Value("\${cryptovault.master.password}")
    private lateinit var masterPassword: String

    private lateinit var loader: MasterSeedLoader

    @BeforeEach
    fun setUp() {
        loader = MasterSeedLoader(masterSeedConfig, masterPassword)
    }

    @Test
    fun `deve carregar master seed em Base64`() {
        val masterSeed = loader.masterSeed()
        assertNotNull(masterSeed)
        assertTrue(masterSeed.getBytes().size > 0)
        assertEquals("test-seed-for-testing", String(masterSeed.getBytes()))
    }

    @Test
    fun `deve descriptografar master seed`() {
        // Primeiro, criptografa um seed de teste
        val plainSeed = "seed-para-teste-de-descriptografia"
        val encrypted = EncryptionServiceTest.encryptForTest(plainSeed, "test-password")

        val loaderWithEnc = MasterSeedLoader("ENC:$encrypted", "test-password")
        val masterSeed = loaderWithEnc.masterSeed()
        assertNotNull(masterSeed)
        assertEquals(plainSeed, String(masterSeed.getBytes()))
    }

    @Test
    fun `deve falhar com senha errada`() {
        val plainSeed = "seed-teste"
        val encrypted = EncryptionServiceTest.encryptForTest(plainSeed, "senha-correta")

        val loader = MasterSeedLoader("ENC:$encrypted", "senha-errada")

        assertThrows<RuntimeException> {
            loader.masterSeed()
        }
    }
}