package com.jminango.cryptovault.service

import com.jminango.cryptovault.config.MasterSeed
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CryptoServiceTest {

    private val masterSeed = MasterSeed("semente-de-teste-para-crypto-service".toByteArray())
    private val cryptoService = CryptoService(masterSeed)

    @Test
    fun `deve gerar carteira deterministica`() {
        val userId = "usuario-teste-123"
        val keyPair1 = cryptoService.derivePrivateKeyHD(userId, 0)
        val keyPair2 = cryptoService.derivePrivateKeyHD(userId, 0)

        assertEquals(keyPair1.privateKey, keyPair2.privateKey)
        assertEquals(keyPair1.publicKey, keyPair2.publicKey)
    }

    @Test
    fun `deve gerar carteiras diferentes para indices diferentes`() {
        val userId = "usuario-teste-456"
        val keyPair0 = cryptoService.derivePrivateKeyHD(userId, 0)
        val keyPair1 = cryptoService.derivePrivateKeyHD(userId, 1)

        assertNotEquals(keyPair0.privateKey, keyPair1.privateKey)
        assertNotEquals(keyPair0.publicKey, keyPair1.publicKey)
    }

    @Test
    fun `deve gerar carteiras diferentes para usuarios diferentes`() {
        val keyPairUser1 = cryptoService.derivePrivateKeyHD("usuario1", 0)
        val keyPairUser2 = cryptoService.derivePrivateKeyHD("usuario2", 0)

        assertNotEquals(keyPairUser1.privateKey, keyPairUser2.privateKey)
    }
}