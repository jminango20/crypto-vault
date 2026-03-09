package com.jminango.cryptovault.controller

import com.jminango.cryptovault.annotation.RateLimit
import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.ValidationException
import com.jminango.cryptovault.service.TransactionService
import com.jminango.cryptovault.service.WalletService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/wallet")
@CrossOrigin(origins = ["*"])
class WalletController(
    private val walletService: WalletService,
    private val transactionService: TransactionService
) {

    /**
     * Creates an HD wallet for the authenticated user.
     * A user can only create a wallet for themselves; one wallet per user is enforced.
     */
    @PostMapping("/create")
    fun createWallet(
        @Valid @RequestBody request: CreateWalletRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletResponse>> {
        logger.info { "Wallet creation request for user: ${request.userId} by: ${authentication.name}" }
        val wallet = walletService.createWallet(request, authentication.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(success = true, data = wallet))
    }

    @GetMapping("/{userId}")
    fun getWallet(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletResponse>> {
        validateUserAccess(authentication.name, userId, "wallet information")
        val wallet = walletService.getWallet(userId)
        return ResponseEntity.ok(ApiResponse(success = true, data = wallet))
    }

    @GetMapping("/{userId}/address")
    fun getWalletAddress(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletAddressResponse>> {
        validateUserAccess(authentication.name, userId, "wallet address")
        val addressInfo = walletService.getWalletAddress(userId)
        return ResponseEntity.ok(ApiResponse(success = true, data = addressInfo))
    }

    @GetMapping("/{userId}/balance/native")
    fun getNativeBalance(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletBalanceResponse>> {
        validateUserAccess(authentication.name, userId, "balance")
        val wallet = walletService.getWallet(userId)
        val balance = transactionService.getWalletBalance(wallet.address)
        return ResponseEntity.ok(ApiResponse(success = true, data = balance))
    }

    /** Enforces that users can only access their own wallet data. */
    private fun validateUserAccess(authenticatedUser: String, requestedUser: String, resource: String) {
        if (authenticatedUser != requestedUser) {
            logger.warn { "Unauthorized: $authenticatedUser attempted to access $resource for $requestedUser" }
            throw ValidationException(mapOf("authorization" to "You can only access your own $resource"))
        }
    }
}
