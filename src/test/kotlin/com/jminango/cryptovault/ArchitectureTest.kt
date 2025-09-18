package com.jminango.cryptovault

import com.jminango.cryptovault.dto.CreateWalletRequest
import com.jminango.cryptovault.service.CryptoService
import com.jminango.cryptovault.service.EncryptionService
import com.jminango.cryptovault.service.WalletService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ArchitectureTest {

    @Autowired lateinit var cryptoService: CryptoService
    @Autowired lateinit var encryptionService: EncryptionService
    @Autowired lateinit var walletService: WalletService

    @Test
    fun `serviços devem ser independentes`() {
        // Testar que cada serviço funciona independentemente

        // 1. EncryptionService
        val encrypted = encryptionService.encrypt("test")
        val decrypted = encryptionService.decrypt(encrypted)
        assertEquals("test", decrypted)

        // 2. CryptoService
        val key1 = cryptoService.derivePrivateKeyHD("user1", 0)
        assertNotNull(key1)

        // 3. WalletService usa ambos sem problemas
        val wallet = walletService.createWallet(CreateWalletRequest("testuser"))
        assertNotNull(wallet.address)
    }

}