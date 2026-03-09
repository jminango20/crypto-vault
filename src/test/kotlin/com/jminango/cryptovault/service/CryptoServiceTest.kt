package com.jminango.cryptovault.service

import com.jminango.cryptovault.config.MasterSeed
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CryptoServiceTest {

    private val masterSeed = MasterSeed("semente-de-teste-para-crypto-service".toByteArray())
    private val cryptoService = CryptoService(
        masterSeed = masterSeed,
        maxDerivationAttempts = 10
    )

    @Test
    fun `deve gerar carteira deterministica para o mesmo indice`() {
        val keyPair1 = cryptoService.derivePrivateKeyHD(0)
        val keyPair2 = cryptoService.derivePrivateKeyHD(0)

        assertEquals(keyPair1.privateKey, keyPair2.privateKey)
        assertEquals(keyPair1.publicKey, keyPair2.publicKey)
    }

    @Test
    fun `deve gerar carteiras diferentes para indices diferentes`() {
        val keyPair0 = cryptoService.derivePrivateKeyHD(0)
        val keyPair1 = cryptoService.derivePrivateKeyHD(1)

        assertNotEquals(keyPair0.privateKey, keyPair1.privateKey)
        assertNotEquals(keyPair0.publicKey, keyPair1.publicKey)
    }

    @Test
    fun `deve gerar carteiras diferentes para sementes diferentes`() {
        val otherSeed = MasterSeed("outra-semente-completamente-diferente".toByteArray())
        val otherService = CryptoService(masterSeed = otherSeed, maxDerivationAttempts = 10)

        val keyPairA = cryptoService.derivePrivateKeyHD(0)
        val keyPairB = otherService.derivePrivateKeyHD(0)

        assertNotEquals(keyPairA.privateKey, keyPairB.privateKey)
    }

    @Test
    fun `deve derivar chave valida sem lancar excecao`() {
        assertDoesNotThrow {
            val keyPair = cryptoService.derivePrivateKeyHD(0)
            assertNotNull(keyPair)
            assertNotNull(keyPair.privateKey)
            assertNotNull(keyPair.publicKey)
        }
    }
}
