package com.jminango.cryptovault.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * Request para mint de tokens
 */
data class MintTokensRequest(
    @field:NotBlank(message = "From userId is required")
    val from: String,

    @field:NotBlank(message = "To userId is required")
    val to: String,

    @field:Positive(message = "Amount must be greater than 0")
    val amount: Long
)

/**
 * Response de mint
 */
data class MintTokensResponse(
    val transactionHash: String,
    val to: String,
    val amount: Long,
    val blockNumber: Long,
    val gasUsed: Long,
    val status: String
)

/**
 * Request para transfer de tokens
 */
data class TransferTokensRequest(
    @field:NotBlank(message = "From userId is required")
    val from: String,

    @field:NotBlank(message = "To userId is required")
    val to: String,

    @field:Positive(message = "Amount must be greater than 0")
    val amount: Long
)

/**
 * Response de transfer
 */
data class TransferTokensResponse(
    val transactionHash: String,
    val from: String,
    val to: String,
    val amount: Long,
    val blockNumber: Long,
    val gasUsed: Long,
    val status: String
)

/**
 * Response de saldo de tokens
 */
data class TokenBalanceResponse(
    val userId: String,
    val address: String,
    val balance: Long,
    val symbol: String,
    val contractAddress: String
)