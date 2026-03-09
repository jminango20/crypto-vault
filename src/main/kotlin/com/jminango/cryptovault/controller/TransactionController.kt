package com.jminango.cryptovault.controller

import com.jminango.cryptovault.annotation.RateLimit
import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.ValidationException
import com.jminango.cryptovault.service.TransactionService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Controller para transações nativas (ETH) na blockchain
 */
@RestController
@RequestMapping("/api/v1/transaction")
@CrossOrigin(origins = ["*"])
class TransactionController(
    private val transactionService: TransactionService
) {

    /**
     * Assinar transação (sem enviar para blockchain)
     *
     * POST /api/v1/transaction/sign
     * Rate limit: 10 requisições por minuto
     */
    @PostMapping("/sign")
    //@RateLimit(maxRequests = 10, durationSeconds = 60)
    fun signTransaction(
        @Valid @RequestBody request: SignTransactionRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<SignTransactionResponse>> {
        val authenticatedUsername = authentication.name

        logger.info {
            "User $authenticatedUsername signing transaction: ${request.from} -> ${request.to}"
        }

        val signed = transactionService.signTransaction(request)

        logger.debug { "Transaction signed: ${signed.transactionHash}" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = signed
            )
        )
    }

    /**
     * Assinar E enviar transação para blockchain
     *
     * POST /api/v1/transaction/send
     * Rate limit: 10 requisições por minuto
     */
    @PostMapping("/send")
    //@RateLimit(maxRequests = 10, durationSeconds = 60)
    fun sendTransaction(
        @Valid @RequestBody request: SendTransactionRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<SendTransactionResponse>> {
        val authenticatedUsername = authentication.name

        logger.info {
            "User $authenticatedUsername sending transaction to blockchain: " +
                    "${request.from} -> ${request.to}"
        }

        val result = transactionService.signAndSendTransaction(request)

        logger.debug {
            "Transaction sent and confirmed: ${result.txHash} " +
                    "(block: ${result.blockNumber}, status: ${result.status})"
        }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = result
            )
        )
    }

    /**
     * Consultar histórico de transações do usuário
     *
     * GET /api/v1/transaction/history/{userId}
     * Rate limit: 50 requisições por minuto
     */
    @GetMapping("/history/{userId}")
    //@RateLimit(maxRequests = 50, durationSeconds = 60)
    fun getTransactionHistory(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<List<TransactionHistoryResponse>>> {
        val authenticatedUsername = authentication.name

        logger.info {
            "Transaction history request for user: $userId by $authenticatedUsername"
        }

        // Validar autorização
        validateUserAccess(authenticatedUsername, userId, "transaction history")

        val transactions = transactionService.getTransactionHistory(userId)

        logger.debug { "Retrieved ${transactions.size} transactions for user: $userId" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = transactions
            )
        )
    }

    /**
     * Valida se usuário pode acessar dados de outro usuário
     */
    private fun validateUserAccess(
        authenticatedUser: String,
        requestedUser: String,
        resource: String
    ) {
        if (authenticatedUser != requestedUser) {
            logger.warn {
                "Unauthorized access attempt: $authenticatedUser tried to access " +
                        "$resource for $requestedUser"
            }
            throw ValidationException(
                mapOf("authorization" to "You can only access your own $resource")
            )
        }
    }
}