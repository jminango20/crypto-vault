package com.jminango.cryptovault.controller

import com.jminango.cryptovault.annotation.RateLimit
import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.service.WalletService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
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
    fun createWallet(@RequestBody request: CreateWalletRequest): ResponseEntity<ApiResponse<WalletResponse>> {
        val wallet = walletService.createWallet(request)
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = wallet
        ))
    }

    @GetMapping("/wallet/{userId}")
    fun getWallet(@PathVariable userId: String): ResponseEntity<ApiResponse<WalletResponse>> {
        val wallet = walletService.getWallet(userId)
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = wallet
        ))
    }

    @GetMapping("/wallet/{userId}/address")
    @RateLimit(maxRequests = 30, durationSeconds = 60)
    fun getWalletAddress(@PathVariable userId: String): ResponseEntity<ApiResponse<WalletAddressResponse>> {
        val addressInfo = walletService.getWalletAddress(userId)
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = addressInfo
        ))
    }

    @PostMapping("/transaction/sign")
    @RateLimit(maxRequests = 10, durationSeconds = 60)
    fun signTransaction(@RequestBody request: SignTransactionRequest): ResponseEntity<ApiResponse<SignTransactionResponse>> {
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