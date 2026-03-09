package com.jminango.cryptovault.service

import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.*
import com.jminango.cryptovault.model.Transaction
import com.jminango.cryptovault.repository.TransactionRepository
import com.jminango.cryptovault.repository.WalletRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.*
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

@Service
class TransactionService(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val cryptoService: CryptoService,
    private val encryptionService: EncryptionService,
    private val blockchainService: BlockchainService,
    @Value("\${cryptovault.signing.chain.id}") private val chainId: Long,
    @Value("\${cryptovault.signing.gas.price}") private val gasPrice: Long,
    @Value("\${cryptovault.signing.gas.limit}") private val gasLimit: Long
) {

    /**
     * Assina uma transação (SEM enviar para blockchain)
     * Útil quando usuário quer apenas a assinatura
     */
    @Transactional
    fun signTransaction(request: SignTransactionRequest): SignTransactionResponse {
        logger.info { "Signing transaction from ${request.from} to ${request.to}" }

        val wallet = walletRepository.findByAddress(request.from)
            .orElseThrow { WalletNotFoundException(request.from) }

        try {
            // Decrypt derivation index; GCM tag + AAD verify integrity and ownership
            val walletIndex = encryptionService.decrypt(wallet.derivationPath, aad = wallet.userId).toInt()

            // Re-derive private key — never stored, derived on demand
            val keyPair = cryptoService.derivePrivateKeyHD(walletIndex = walletIndex)

            val credentials = Credentials.create(keyPair)

            // Validação de segurança
            require(credentials.address.equals(request.from, ignoreCase = true)) {
                "Security error: Address mismatch!"
            }

            // Obter nonce
            val nonce = request.nonce?.let { BigInteger.valueOf(it) }
                ?: throw InvalidTransactionException("Nonce is required")

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

            val transaction = Transaction(
                walletId = wallet.id,
                txHash = txHash,
                fromAddress = request.from,
                toAddress = request.to,
                data = request.data,
                status = "SIGNED"
            )
            transactionRepository.save(transaction)

            logger.debug { "Transaction saved to database: $txHash" }

            return SignTransactionResponse(
                signedTransaction = hexValue,
                transactionHash = txHash,
                from = request.from,
                to = request.to,
                nonce = nonce.toLong()
            )

        } catch (e: CryptoVaultException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to sign transaction" }
            throw TransactionSigningException("Failed to sign transaction", e)
        }
    }

    /**
     * Assina E envia transação para blockchain
     */
    @Transactional
    fun signAndSendTransaction(request: SendTransactionRequest): SendTransactionResponse {
        logger.info { "Signing and sending transaction from ${request.from} to ${request.to}" }

        val wallet = walletRepository.findByAddress(request.from)
            .orElseThrow { WalletNotFoundException(request.from) }

        try {
            // 1. Decrypt derivation index; GCM tag + AAD verify integrity and ownership
            val walletIndex = encryptionService.decrypt(wallet.derivationPath, aad = wallet.userId).toInt()

            // 2. Re-derive private key — never stored, derived on demand
            val keyPair = cryptoService.derivePrivateKeyHD(walletIndex = walletIndex)

            val credentials = Credentials.create(keyPair)

            // 3. Validação de segurança
            require(credentials.address.equals(request.from, ignoreCase = true)) {
                "Security error: Address mismatch!"
            }

            // 4. Obter nonce da BLOCKCHAIN
            val nonce = blockchainService.getNonce(request.from)

            logger.debug { "Using nonce from blockchain: $nonce" }

            // 5. Criar e assinar transação
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

            // 6. Salvar no DB com status PENDING
            val transaction = Transaction(
                walletId = wallet.id,
                txHash = txHash,
                fromAddress = request.from,
                toAddress = request.to,
                data = request.data,
                status = "PENDING"
            )
            transactionRepository.save(transaction)

            logger.debug { "Transaction saved with status PENDING: $txHash" }

            // 7. Enviar para blockchain
            val sentTxHash = blockchainService.sendRawTransaction(hexValue)

            logger.info { "Transaction sent to blockchain: $sentTxHash" }

            // 8. Aguardar confirmação
            val receipt = try {
                blockchainService.waitForReceipt(sentTxHash)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get receipt, transaction may still be pending" }

                // Retorna resposta com status PENDING
                return SendTransactionResponse(
                    txHash = sentTxHash,
                    from = request.from,
                    to = request.to,
                    value = request.value ?: "0",
                    status = "PENDING",
                    blockNumber = null,
                    gasUsed = null
                )
            }

            // 9. Verificar sucesso
            val success = blockchainService.isTransactionSuccessful(receipt)
            val finalStatus = if (success) "CONFIRMED" else "FAILED"

            // 10. Atualizar status no DB
            updateTransactionStatus(sentTxHash, finalStatus)

            logger.info {
                "Transaction $finalStatus in block ${receipt.blockNumber}: $sentTxHash"
            }

            return SendTransactionResponse(
                txHash = sentTxHash,
                from = request.from,
                to = request.to,
                value = request.value ?: "0",
                status = finalStatus,
                blockNumber = receipt.blockNumber?.toLong(),
                gasUsed = receipt.gasUsed?.toLong()
            )

        } catch (e: CryptoVaultException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to sign and send transaction" }
            throw TransactionSigningException("Failed to sign and send transaction", e)
        }
    }

    /**
     * Consulta histórico de transações de um usuário
     */
    fun getTransactionHistory(userId: String): List<TransactionHistoryResponse> {
        logger.info { "Fetching transaction history for user: $userId" }

        val wallet = walletRepository.findByUserId(userId)
            .orElseThrow { WalletNotFoundException(userId) }

        val transactions = transactionRepository.findByWalletId(wallet.id)

        return transactions.map { tx ->
            TransactionHistoryResponse(
                txHash = tx.txHash,
                fromAddress = tx.fromAddress,
                toAddress = tx.toAddress,
                status = tx.status,
                createdAt = tx.createdAt
            )
        }
    }

    /**
     * Atualiza status de uma transação
     */
    @Transactional
    fun updateTransactionStatus(txHash: String, newStatus: String) {
        logger.info { "Updating transaction status: $txHash -> $newStatus" }

        val transaction = transactionRepository.findByTxHash(txHash)
            .orElseThrow {
                InvalidTransactionException("Transaction not found: $txHash")
            }

        // Criar nova instância com status atualizado
        val updated = transaction.copy(status = newStatus)
        transactionRepository.save(updated)

        logger.debug { "Transaction status updated: $txHash" }
    }

    /**
     * Consulta saldo de uma carteira
     */
    fun getWalletBalance(address: String): WalletBalanceResponse {
        logger.info { "Fetching balance for address: $address" }

        val balanceWei = blockchainService.getBalance(address)

        // Converter de Wei para Ether (1 ETH = 10^18 Wei)
        val balanceEth = balanceWei.toBigDecimal().divide(BigDecimal("1000000000000000000"))

        return WalletBalanceResponse(
            address = address,
            balanceWei = balanceWei.toString(),
            balanceEth = balanceEth.toPlainString()
        )
    }
}