package com.jminango.cryptovault.controller

import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.service.WalletService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
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
    fun createWallet(@RequestBody request: CreateWalletRequest): ResponseEntity<ApiResponse<WalletResponse>> {
        return try {
            val wallet = walletService.createWallet(request)
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = wallet
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error creating wallet" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse(
                    success = false,
                    error = e.message
                ))
        }
    }


    @GetMapping("/wallet/{userId}")
    fun getWallet(@PathVariable userId: String): ResponseEntity<ApiResponse<WalletResponse>> {
        val wallet = walletService.getWallet(userId)
        return if (wallet != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = wallet
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse(
                    success = false,
                    error = "Wallet not found"
                ))
        }
    }

    @PostMapping("/transaction/sign")
    fun signTransaction(@RequestBody request: SignTransactionRequest): ResponseEntity<ApiResponse<SignTransactionResponse>> {
        return try {
            val signed = walletService.signTransaction(request)
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = signed
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error signing transaction" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse(
                    success = false,
                    error = e.message
                ))
        }
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