package com.jminango.cryptovault.config

import com.jminango.cryptovault.service.EncryptionServiceTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.util.Base64

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    // Plain-text seed >= 32 bytes so the Spring context initialises successfully.
    "cryptovault.master.seed=test-seed-for-testing-min-32-bit",
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
        // Create a separate loader to test Base64 decoding; seed must be >= 32 bytes.
        val plainSeed = "test-seed-for-testing-min-32-bit"  // exactly 32 bytes
        val base64Seed = "BASE64:${Base64.getEncoder().encodeToString(plainSeed.toByteArray())}"

        val base64Loader = MasterSeedLoader(base64Seed, masterPassword)
        val masterSeed = base64Loader.masterSeed()

        assertNotNull(masterSeed)
        assertEquals(plainSeed, String(masterSeed.getBytes()))
    }

    @Test
    fun `deve descriptografar master seed`() {
        val plainSeed = "seed-para-teste-de-descriptografia"
        val encrypted = EncryptionServiceTest.encryptForTest(plainSeed, "test-password")

        val loaderWithEnc = MasterSeedLoader("ENC:$encrypted", "test-password")
        val masterSeed = loaderWithEnc.masterSeed()

        assertNotNull(masterSeed)
        assertEquals(plainSeed, String(masterSeed.getBytes()))
    }

    @Test
    fun `deve falhar com senha errada`() {
        val plainSeed = "seed-para-teste-de-descriptografia"
        val encrypted = EncryptionServiceTest.encryptForTest(plainSeed, "senha-correta")

        val badLoader = MasterSeedLoader("ENC:$encrypted", "senha-errada")

        assertThrows<RuntimeException> {
            badLoader.masterSeed()
        }
    }
}
