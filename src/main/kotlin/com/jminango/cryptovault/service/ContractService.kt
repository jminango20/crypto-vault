package com.jminango.cryptovault.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.InvalidTransactionException
import com.jminango.cryptovault.repository.TransactionRepository
import com.jminango.cryptovault.repository.WalletRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

@Service
class ContractService(
    private val web3j: Web3j,
    private val blockchainService: BlockchainService,
    private val cryptoService: CryptoService,
    private val encryptionService: EncryptionService,
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${cryptovault.signing.chain.id}") private val chainId: Long,
    @Value("\${cryptovault.signing.gas.price}") private val gasPrice: Long,
    @Value("\${cryptovault.signing.gas.limit}") private val gasLimit: Long
) {

    companion object {
        private const val CONTRACT_JSON_PATH = "contracts/CoffeeToken.json"
    }

    /**
     * Mint tokens (cooperativa registra sacas para produtor)
     */
    fun mintTokens(request: MintTokensRequest): MintTokensResponse {
        logger.info { "Minting ${request.amount} tokens to ${request.from}" }

        try {
            val contractAddress = getContractAddress()

            // 1. Buscar wallet da cooperativa (owner)
            val ownerWallet = walletRepository.findByUserId(request.from)
                .orElseThrow {
                    InvalidTransactionException("Wallet not found: ${request.from}")
                }

            // 2. Buscar wallet do produtor (recipient)
            val recipientWallet  = walletRepository.findByUserId(request.to)
                .orElseThrow {
                    InvalidTransactionException("Wallet not found: ${request.to}")
                }

            val ownerAddress = ownerWallet.address
            val recipientAddress = recipientWallet.address

            // 2. Decrypt derivation index; GCM tag + AAD verify integrity and ownership
            val walletIndex = encryptionService.decrypt(ownerWallet.derivationPath, aad = ownerWallet.userId).toInt()
            val keyPair = cryptoService.derivePrivateKeyHD(walletIndex)
            val credentials = Credentials.create(keyPair)

            // 4. Validação de segurança
            require(credentials.address.equals(ownerAddress, ignoreCase = true)) {
                "Security error: Address mismatch!"
            }

            // 5. Criar função mint(address to, uint256 amount)
            val function = Function(
                "mint",
                listOf(
                    Address(recipientAddress),
                    Uint256(BigInteger.valueOf(request.amount))
                ),
                emptyList()
            )

            val encodedFunction = FunctionEncoder.encode(function)

            // 6. Obter nonce
            val nonce = blockchainService.getNonce(ownerAddress)

            logger.debug { "Creating mint transaction with nonce: $nonce" }

            // 7. Criar transação
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                contractAddress,
                BigInteger.ZERO,
                encodedFunction
            )

            // 8. Assinar
            val signedMessage = TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                credentials
            )

            val hexValue = Numeric.toHexString(signedMessage)

            logger.info { "Mint transaction signed" }

            // 9. Enviar para blockchain
            val txHash = blockchainService.sendRawTransaction(hexValue)

            logger.info { "Mint transaction sent: $txHash" }

            val transaction = com.jminango.cryptovault.model.Transaction(
                walletId = ownerWallet.id,
                txHash = txHash,
                fromAddress = ownerAddress,
                toAddress = recipientAddress,
                data = encodedFunction,
                status = "PENDING"
            )
            transactionRepository.save(transaction)

            // 10. Aguardar confirmação
            val receipt = blockchainService.waitForReceipt(txHash)

            val success = blockchainService.isTransactionSuccessful(receipt)

            if (!success) {
                logger.error {
                    "Mint transaction FAILED! TxHash: $txHash, Status: ${receipt.status}"
                }
                throw InvalidTransactionException("Mint transaction failed in blockchain")
            }

            logger.info { "Successfully minted ${request.amount} tokens to ${request.to}" }

            val updated = transaction.copy(status = "CONFIRMED")
            transactionRepository.save(updated)

            return MintTokensResponse(
                transactionHash = txHash,
                to = request.to,
                amount = request.amount,
                blockNumber = receipt.blockNumber.toLong(),
                gasUsed = receipt.gasUsed.toLong(),
                status = "CONFIRMED"
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to mint tokens" }
            throw InvalidTransactionException("Mint failed: ${e.message}")
        }
    }

    /**
     * Transfer tokens (produtor vende/transfere sacas)
     */
    fun transferTokens(request: TransferTokensRequest): TransferTokensResponse {
        logger.info { "Transferring ${request.amount} tokens from ${request.from} to ${request.to}" }

        try {
            val contractAddress = getContractAddress()

            // 1. Buscar wallet de quem vai transferir
            val senderWallet = walletRepository.findByUserId(request.from)
                .orElseThrow {
                    InvalidTransactionException("Sender wallet not found for user: ${request.from}")
                }

            // 2. Buscar wallet de quem vai receber
            val recipientWallet = walletRepository.findByUserId(request.to)
                .orElseThrow {
                    InvalidTransactionException("Recipient wallet not found for user: ${request.to}")
                }

            val senderAddress = senderWallet.address
            val recipientAddress = recipientWallet.address

            // 3. Decrypt derivation index; GCM tag + AAD verify integrity and ownership
            val walletIndex = encryptionService.decrypt(senderWallet.derivationPath, aad = senderWallet.userId).toInt()
            val keyPair = cryptoService.derivePrivateKeyHD(walletIndex)
            val credentials = Credentials.create(keyPair)

            // 4. Criar função transfer(address to, uint256 amount)
            val function = Function(
                "transfer",
                listOf(
                    Address(recipientAddress),
                    Uint256(BigInteger.valueOf(request.amount))
                ),
                emptyList()
            )

            val encodedFunction = FunctionEncoder.encode(function)

            // 5. Obter nonce
            val nonce = blockchainService.getNonce(senderAddress)

            // 6. Criar e assinar transação
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                contractAddress,
                BigInteger.ZERO,
                encodedFunction
            )

            val signedMessage = TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                credentials
            )

            val hexValue = Numeric.toHexString(signedMessage)

            // 7. Enviar
            val txHash = blockchainService.sendRawTransaction(hexValue)

            logger.info { "Transfer transaction sent: $txHash" }

            val transaction = com.jminango.cryptovault.model.Transaction(
                walletId = senderWallet.id,
                txHash = txHash,
                fromAddress = senderAddress,
                toAddress = recipientAddress,
                data = encodedFunction,
                status = "PENDING"
            )
            transactionRepository.save(transaction)

            // 8. Aguardar confirmação
            val receipt = blockchainService.waitForReceipt(txHash)

            val success = blockchainService.isTransactionSuccessful(receipt)

            if (!success) {
                throw InvalidTransactionException("Transfer transaction failed")
            }

            logger.info { "Successfully transferred ${request.amount} tokens" }

            val updated = transaction.copy(status = "CONFIRMED")
            transactionRepository.save(updated)

            return TransferTokensResponse(
                transactionHash = txHash,
                from = request.from,
                to = request.to,
                amount = request.amount,
                blockNumber = receipt.blockNumber.toLong(),
                gasUsed = receipt.gasUsed.toLong(),
                status = "CONFIRMED"
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to transfer tokens" }
            throw InvalidTransactionException("Transfer failed: ${e.message}")
        }
    }

    /**
     * Consultar saldo de tokens por userId
     */
    fun getTokenBalance(userId: String): TokenBalanceResponse {
        logger.info { "Getting token balance for: $userId" }

        try {
            val wallet = walletRepository.findByUserId(userId)
                .orElseThrow {
                    InvalidTransactionException("Wallet not found: $userId")
                }

            val address = wallet.address

            val contractAddress = getContractAddress()

            // Criar função balanceOf(address)
            val function = Function(
                "balanceOf",
                listOf(Address(address)),
                listOf(object : TypeReference<Uint256>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)

            // Fazer call (não gasta gas)
            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    address,
                    contractAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            val value = response.value

            // Decodificar resposta
            val result = FunctionReturnDecoder.decode(
                value,
                function.outputParameters
            )

            val balance = (result[0].value as BigInteger).toLong()

            logger.info { "Token balance for $userId: $balance CCF" }

            return TokenBalanceResponse(
                userId = userId,
                address = address,
                balance = balance,
                symbol = "CCF",
                contractAddress = contractAddress
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get token balance" }
            throw InvalidTransactionException("Balance query failed: ${e.message}")
        }
    }

    /**
     * Obtém informações do contrato
     */
    fun getContractInfo(): Map<String, Any> {
        val contractData = loadContractData()
        val address = contractData["address"] as String

        return mapOf(
            "contractName" to "CoffeeToken",
            "symbol" to "CCF",
            "address" to address,
            "description" to "Token para sacas de café",
            "network" to "Kaleido"
        )
    }

    /**
     * Obtém endereço do contrato do JSON
     */
    private fun getContractAddress(): String {
        val contractData = loadContractData()
        return contractData["address"] as String
    }

    /**
     * Carrega dados do contrato do JSON
     */
    private fun loadContractData(): Map<String, Any> {
        try {
            val resource = ClassPathResource(CONTRACT_JSON_PATH)
            val inputStream = resource.inputStream

            @Suppress("UNCHECKED_CAST")
            return objectMapper.readValue(inputStream, Map::class.java) as Map<String, Any>

        } catch (e: Exception) {
            logger.error(e) { "Failed to load contract data" }
            throw RuntimeException("Failed to load contract data", e)
        }
    }
}