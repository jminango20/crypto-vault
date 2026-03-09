package com.jminango.cryptovault.service

import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.*
import com.jminango.cryptovault.model.Wallet
import com.jminango.cryptovault.repository.WalletRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.*

private val logger = KotlinLogging.logger {}

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val cryptoService: CryptoService,        // Para HD wallets
    private val encryptionService: EncryptionService, // Para criptografar dados
    @Value("\${cryptovault.signing.chain.id}") private val chainId: Long,
    @Value("\${cryptovault.signing.gas.price}") private val gasPrice: Long,
    @Value("\${cryptovault.signing.gas.limit}") private val gasLimit: Long
) {

    /**
     * Create a wallet
     */
    @Transactional
    fun createWallet(request: CreateWalletRequest, authenticatedUsername: String): WalletResponse {
        logger.info {
            "Wallet creation request for user: ${request.userId} by: $authenticatedUsername"
        }

        // Validação de autorização
        if (request.userId != authenticatedUsername) {
            logger.warn {
                "Unauthorized: $authenticatedUsername attempted to create wallet for ${request.userId}"
            }
            throw ValidationException(
                mapOf("userId" to "You can only create your own wallet")
            )
        }

        // Verificar se já existe
        if (walletRepository.findByUserId(request.userId).isPresent) {
            logger.warn { "Wallet already exists for user: ${request.userId}" }
            throw WalletAlreadyExistsException(request.userId)
        }

        try {
            // Assign next global sequential index: MAX(walletIndex) + 1, or 0 for the first wallet
            val nextIndex = walletRepository.findMaxWalletIndex() + 1

            // Derive HD wallet from master seed at the sequential index
            val keyPair = cryptoService.derivePrivateKeyHD(walletIndex = nextIndex)
            val credentials = Credentials.create(keyPair)

            // Encrypt only the derivation index; userId is bound as AAD so swapping
            // this ciphertext into another user's record causes decryption to fail.
            val encryptedPath = encryptionService.encrypt(
                nextIndex.toString(),
                aad = request.userId
            )

            // Salvar wallet
            val wallet = Wallet(
                userId = request.userId,
                address = credentials.address,
                walletIndex = nextIndex,
                derivationPath = encryptedPath,
                network = request.network
            )

            val saved = walletRepository.save(wallet)

            logger.info {
                "Wallet created successfully - userId: ${saved.userId}, address: ${saved.address}"
            }


            return WalletResponse(
                userId = saved.userId,
                address = saved.address,
                network = saved.network,
                createdAt = saved.createdAt
            )

        } catch (e: CryptoVaultException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error creating wallet for user: ${request.userId}" }
            throw KeyDerivationException("Failed to create wallet", e)
        }
    }

    /**
     * Get a wallet
     */
    fun getWallet(userId: String): WalletResponse {
        logger.debug { "Fetching wallet details for user: $userId" }

        val wallet = walletRepository.findByUserId(userId)
            .orElseThrow {
                logger.warn { "Wallet not found for user: $userId" }
                WalletNotFoundException(userId)
            }

        return WalletResponse(
            userId = wallet.userId,
            address = wallet.address,
            network = wallet.network,
            createdAt = wallet.createdAt
        )
    }

    /**
     * Get wallet address
     */
    fun getWalletAddress(userId: String): WalletAddressResponse {
        logger.debug { "Fetching wallet address for user: $userId" }

        val wallet = walletRepository.findByUserId(userId)
            .orElseThrow {
                logger.warn { "Wallet not found for user: $userId" }
                WalletNotFoundException(userId)
            }

        return WalletAddressResponse(
            userId = wallet.userId,
            address = wallet.address,
            network = wallet.network
        )
    }
}