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