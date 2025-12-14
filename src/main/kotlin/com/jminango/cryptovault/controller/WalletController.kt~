package com.jminango.cryptovault.controller

import com.jminango.cryptovault.annotation.RateLimit
import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.ValidationException
import com.jminango.cryptovault.service.WalletService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
class WalletController(
    private val walletService: WalletService
) {

    @PostMapping("/wallet/create")
    @RateLimit(maxRequests = 5, durationSeconds = 60)
    fun createWallet(
        @Valid @RequestBody request: CreateWalletRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletResponse>> {

        val authenticatedUsername = authentication.name


        val wallet = walletService.createWallet(request, authenticatedUsername)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = wallet
        ))
    }

    @GetMapping("/wallet/{userId}")
    @RateLimit(maxRequests = 20, durationSeconds = 60)
    fun getWallet(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletResponse>> {

        val authenticatedUsername = authentication.name

        if (userId != authenticatedUsername) {
            logger.warn {
                "User $authenticatedUsername tried to access wallet for $userId"
            }
            throw ValidationException(
                mapOf("userId" to "You just can access your own wallet")
            )
        }

        val wallet = walletService.getWallet(userId)
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = wallet
        ))
    }

    @GetMapping("/wallet/{userId}/address")
    @RateLimit(maxRequests = 30, durationSeconds = 60)
    fun getWalletAddress(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<WalletAddressResponse>> {

        val authenticatedUsername = authentication.name

        if (userId != authenticatedUsername) {
            throw ValidationException(
                mapOf("userId" to "You just can access your own wallet")
            )
        }

        val addressInfo = walletService.getWalletAddress(userId)
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = addressInfo
        ))
    }

    @PostMapping("/transaction/sign")
    @RateLimit(maxRequests = 10, durationSeconds = 60)
    fun signTransaction(
        @Valid @RequestBody request: SignTransactionRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<SignTransactionResponse>> {

        val authenticatedUsername = authentication.name
        logger.info { "User $authenticatedUsername is trying to sign a transaction" }

        val signed = walletService.signTransaction(request)
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = signed
        ))
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "CryptoVault",
            "version" to "1.0.0",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}