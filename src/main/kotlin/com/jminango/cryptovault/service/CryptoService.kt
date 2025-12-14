package com.jminango.cryptovault.service

import com.jminango.cryptovault.config.MasterSeed
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.web3j.crypto.*
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

/**
 * Serviço de criptografia para HD Wallets
 * Depende apenas do MasterSeed (injetado como Bean)
 */
@Service
class CryptoService(
    private val masterSeed: MasterSeed // Injetado pelo Spring
) {

    companion object {
        private const val SALT_DERIVATION_CONSTANT = "SALT_DERIVATION_V1"

        private val SECP256K1_N = BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
            16
        )
    }

    /**
     * Deriva chave privada HD (BIP-44)
     */
    fun derivePrivateKeyHD(
        userId: String,
        walletIndex: Int = 0
    ): ECKeyPair {
        try {

            val salt = deriveUserSalt(userId)

            val userEntropy = generateUserEntropy(userId, salt.toString())

            val mnemonic = MnemonicUtils.generateMnemonic(userEntropy)

            val seed = MnemonicUtils.generateSeed(mnemonic, "")

            val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)

            // BIP-44 path: m/44'/60'/0'/0/index -> m/44'/60'/0'/0/0 primeira carteira do usuario
            val derivationPath = intArrayOf(
                44 or Bip32ECKeyPair.HARDENED_BIT,  // 44' - BIP-44 padrão de carteiras multi-moeda
                60 or Bip32ECKeyPair.HARDENED_BIT,  // 60' (Ethereum)
                0 or Bip32ECKeyPair.HARDENED_BIT,   // 0' (Account)
                0,                                    // 0 (External)
                walletIndex                          // Index do endereço  (0=primeiro, 1=segundo, etc.)
            )

            val childKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, derivationPath)

            // Validar chave
            if (!isValidPrivateKey(childKeyPair.privateKey)) {
                logger.warn { "Invalid key for user $userId, retrying with index ${walletIndex + 1}" }
                return derivePrivateKeyHD(userId, walletIndex + 1)
            }

            logger.debug { "HD wallet derived for user: $userId, index: $walletIndex" }
            return childKeyPair

        } catch (e: Exception) {
            logger.error(e) { "Failed to derive HD key for user: $userId" }
            throw RuntimeException("HD derivation failed", e)
        }
    }

    /**
     * Gera entropia determinística para usuário
     */
    private fun generateUserEntropy(userId: String, salt: String): ByteArray {
        val seedBytes = masterSeed.getBytes()
        val combined = seedBytes + userId.toByteArray(Charsets.UTF_8) + salt.toByteArray()

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined)

        // 16 bytes = 128 bits = 12 palavras mnemonic
        return hash.sliceArray(0..15)
    }

    /**
     * Valida chave privada Ethereum
     */
    private fun isValidPrivateKey(privateKey: BigInteger): Boolean {
        // Ordem da curva secp256k1
        return privateKey > BigInteger.ZERO && privateKey < SECP256K1_N
    }

    /**
     * Derivar um salt deterministico
     */
    private fun deriveUserSalt(userId: String): ByteArray {
        val seedBytes = masterSeed.getBytes()

        val input = seedBytes +
                    userId.toByteArray(Charsets.UTF_8) +
                    SALT_DERIVATION_CONSTANT.toByteArray(Charsets.UTF_8)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }
}