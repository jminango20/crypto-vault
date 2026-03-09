package com.jminango.cryptovault.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

@Service
class BlockchainService(
    private val web3j: Web3j
) {

    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 40
        private const val DEFAULT_SLEEP_DURATION = 1000L
        private const val SUCCESS_STATUS = "0x1"
        private const val FAILURE_STATUS = "0x0"
    }

    /**
     * Obtém o nonce atual de um endereço
     */
    fun getNonce(address: String): BigInteger {
        logger.debug { "Getting nonce for address: $address" }

        val nonce = web3j.ethGetTransactionCount(
            address,
            DefaultBlockParameterName.PENDING
        ).send().transactionCount

        logger.debug { "Nonce for $address: $nonce" }
        return nonce
    }

    /**
     * Envia uma transação assinada para a blockchain
     */
    fun sendRawTransaction(signedTransaction: String): String {
        logger.info { "Sending raw transaction to blockchain" }

        val response = web3j.ethSendRawTransaction(signedTransaction).send()

        if (response.hasError()) {
            val error = response.error
            logger.error { "Blockchain error: ${error.message} (code: ${error.code})" }
            throw RuntimeException("Blockchain error: ${error.message}")
        }

        val txHash = response.transactionHash
        logger.info { "Transaction sent successfully: $txHash" }

        return txHash
    }

    /**
     * Aguarda confirmação da transação (com timeout)
     */
    fun waitForReceipt(
        txHash: String,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS ,
        sleepDuration: Long = DEFAULT_SLEEP_DURATION
    ): TransactionReceipt {
        logger.info { "Waiting for transaction receipt: $txHash" }

        repeat(maxAttempts) { attempt ->
            val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt

            if (receipt.isPresent) {
                val txReceipt = receipt.get()
                logger.info {
                    "Transaction confirmed in block ${txReceipt.blockNumber}: $txHash"
                }
                return txReceipt
            }

            logger.debug { "Attempt ${attempt + 1}/$maxAttempts - waiting for confirmation..." }
            Thread.sleep(sleepDuration)
        }

        throw RuntimeException("Transaction not confirmed after $maxAttempts attempts: $txHash")
    }

    /**
     * Verifica se uma transação foi bem-sucedida
     */
    fun isTransactionSuccessful(receipt: TransactionReceipt): Boolean {
        // Status "0x1" = sucesso, "0x0" = falha
        val success = receipt.status == SUCCESS_STATUS

        if (!success) {
            logger.warn {
                "Transaction failed: ${receipt.transactionHash} " +
                        "(status: ${receipt.status})"
            }
        }

        return success
    }

    /**
     * Obtém o saldo de um endereço
     */
    fun getBalance(address: String): BigInteger {
        logger.debug { "Getting balance for address: $address" }

        val balance = web3j.ethGetBalance(
            address,
            DefaultBlockParameterName.LATEST
        ).send().balance

        logger.debug { "Balance for $address: $balance wei" }
        return balance
    }

    /**
     * Obtém versão do cliente
     */
    fun getClientVersion(): String {
        return web3j.web3ClientVersion().send().web3ClientVersion
    }

    /**
     * Obtém número do último bloco
     */
    fun getLatestBlockNumber(): BigInteger {
        return web3j.ethBlockNumber().send().blockNumber
    }
}