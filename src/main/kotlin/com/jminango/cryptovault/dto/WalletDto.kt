package com.jminango.cryptovault.dto

import java.time.LocalDateTime

data class CreateWalletRequest(
    val userId: String,
    val network: String = "Besu"
)

data class WalletResponse(
    val userId: String,
    val address: String,
    val network: String,
    val createdAt: LocalDateTime
)

data class SignTransactionRequest(
    val from: String,
    val to: String,
    val data: String? = null,
    val value: String? = null,
    val nonce: Long,
    val gasLimit: Long? = null,
    val gasPrice: Long? = null
)

data class SignTransactionResponse(
    val signedTransaction: String,
    val transactionHash: String,
    val from: String,
    val to: String,
    val nonce: Long
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class WalletAddressResponse(
    val userId: String,
    val address: String,
    val network: String
)

data class TransactionHistoryResponse(
    val txHash: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val createdAt: LocalDateTime
)

data class TransactionDetailResponse(
    val id: Long,
    val txHash: String,
    val fromAddress: String,
    val toAddress: String,
    val data: String?,
    val status: String,
    val createdAt: LocalDateTime
)

/**
 * Request para ENVIAR transação (sem nonce manual)
 */
data class SendTransactionRequest(
    val from: String,
    val to: String,
    val value: String? = null,
    val data: String? = null,
    val gasLimit: Long? = null,
    val gasPrice: Long? = null
)

/**
 * Response de transação enviada
 */
data class SendTransactionResponse(
    val txHash: String,
    val from: String,
    val to: String,
    val value: String,
    val status: String,  // PENDING, CONFIRMED, FAILED
    val blockNumber: Long?,
    val gasUsed: Long?
)

/**
 * Response de saldo
 */
data class WalletBalanceResponse(
    val address: String,
    val balanceWei: String,
    val balanceEth: String
)