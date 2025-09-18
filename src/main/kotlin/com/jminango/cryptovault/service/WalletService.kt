package com.jminango.cryptovault.service

import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.model.Wallet
import com.jminango.cryptovault.repository.WalletRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val cryptoService: CryptoService,        // Para HD wallets
    private val encryptionService: EncryptionService, // Para criptografar dados
    @Value("\${cryptovault.blockchain.rpc-url}") private val rpcUrl: String,
    @Value("\${cryptovault.blockchain.chain-id}") private val chainId: Long,
    @Value("\${cryptovault.blockchain.gas-price}") private val gasPrice: Long,
    @Value("\${cryptovault.blockchain.gas-limit}") private val gasLimit: Long
) {
    private val web3j: Web3j = Web3j.build(HttpService(rpcUrl))

    @Transactional
    fun createWallet(request: CreateWalletRequest): WalletResponse {
        logger.info { "Creating wallet for user: ${request.userId}" }

        // Verificar se já existe
        val existingWallet = walletRepository.findByUserId(request.userId)
        if (existingWallet.isPresent) {
            val existing = existingWallet.get()
            logger.info { "Wallet already exists for user: ${request.userId}" }
            return WalletResponse(
                userId = existing.userId,
                address = existing.address,
                network = existing.network,
                createdAt = existing.createdAt
            )
        }

        // Gerar HD wallet
        val walletIndex = 0
        val keyPair = cryptoService.derivePrivateKeyHD(request.userId, walletIndex)
        val credentials = Credentials.create(keyPair)

        // Criptografar informação de derivação
        val derivationInfo = "${request.userId}:$walletIndex"
        val encryptedPath = encryptionService.encrypt(derivationInfo)

        // Salvar wallet
        val wallet = Wallet(
            userId = request.userId,
            address = credentials.address,
            walletIndex = walletIndex,
            derivationPath = encryptedPath,
            network = request.network
        )

        val saved = walletRepository.save(wallet)
        logger.info { "Wallet created: ${saved.address}" }

        return WalletResponse(
            userId = saved.userId,
            address = saved.address,
            network = saved.network,
            createdAt = saved.createdAt
        )
    }

    fun getWallet(userId: String): WalletResponse {
        logger.info { "Fetching wallet for user: $userId" }

        val wallet = walletRepository.findByUserId(userId)
            .orElseThrow { IllegalArgumentException("Wallet not found for user: $userId") }

        return WalletResponse(
            userId = wallet.userId,
            address = wallet.address,
            network = wallet.network,
            createdAt = wallet.createdAt
        )
    }

    fun signTransaction(request: SignTransactionRequest): SignTransactionResponse {
        logger.info { "Signing transaction from ${request.from} to ${request.to}" }

        val wallet = walletRepository.findByAddress(request.from)
            .orElseThrow { IllegalArgumentException("Wallet not found: ${request.from}") }

        // Descriptografar informação de derivação
        val derivationInfo = encryptionService.decrypt(wallet.derivationPath)
        val parts = derivationInfo.split(":")
        val userId = parts[0]
        val walletIndex = parts[1].toInt()

        // Re-derivar a chave (determinística)
        val keyPair = cryptoService.derivePrivateKeyHD(userId, walletIndex)
        val credentials = Credentials.create(keyPair)

        // Validação de segurança
        require(credentials.address.equals(request.from, ignoreCase = true)) {
            "Security error: Address mismatch!"
        }

        // Obter nonce
        val nonce = request.nonce?.let { BigInteger.valueOf(it) }
            ?: getNonce(request.from)

        // Criar e assinar transação
        val rawTransaction = RawTransaction.createTransaction(
            nonce,
            BigInteger.valueOf(gasPrice),
            BigInteger.valueOf(request.gasLimit ?: gasLimit),
            request.to,
            BigInteger(request.value ?: "0"),
            request.data ?: ""
        )

        val signedMessage = TransactionEncoder.signMessage(
            rawTransaction,
            chainId,
            credentials
        )

        val hexValue = Numeric.toHexString(signedMessage)
        val txHash = Hash.sha3(hexValue)

        logger.info { "Transaction signed: $txHash" }

        return SignTransactionResponse(
            signedTransaction = hexValue,
            transactionHash = txHash,
            from = request.from,
            to = request.to,
            nonce = nonce.toLong()
        )
    }

    private fun getNonce(address: String): BigInteger {
        return try {
            web3j.ethGetTransactionCount(
                address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount
        } catch (e: Exception) {
            logger.error(e) { "Failed to get nonce" }
            BigInteger.ZERO
        }
    }
}