package com.jminango.cryptovault.controller

import com.jminango.cryptovault.annotation.RateLimit
import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.exception.ValidationException
import com.jminango.cryptovault.service.ContractService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Controller para interação com smart contract CoffeeToken
 */
@RestController
@RequestMapping("/api/v1/contract")
@CrossOrigin(origins = ["*"])
class ContractController(
    private val contractService: ContractService
) {

    /**
     * Mint tokens (cooperativa registra sacas para produtor)
     *
     * POST /api/v1/contract/mint
     * Rate limit: 10 requisições por minuto
     * Requer: Autenticação JWT
     */
    @PostMapping("/mint")
    //@RateLimit(maxRequests = 10, durationSeconds = 60)
    fun mintTokens(
        @Valid @RequestBody request: MintTokensRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<MintTokensResponse>> {
        val authenticatedUsername = authentication.name

        logger.info {
            "User $authenticatedUsername minting ${request.amount} tokens " +
                    "from ${request.from} to ${request.to}"
        }

        // Validar que usuário autenticado é o owner (from)
        validateUserAuthorization(authenticatedUsername, request.from, "mint tokens")

        val result = contractService.mintTokens(request)

        logger.debug { "Mint successful: ${result.transactionHash}" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = result
            )
        )
    }

    /**
     * Transfer tokens (produtor vende/transfere sacas)
     *
     * POST /api/v1/contract/transfer
     * Rate limit: 20 requisições por minuto
     * Requer: Autenticação JWT
     */
    @PostMapping("/transfer")
    //@RateLimit(maxRequests = 20, durationSeconds = 60)
    fun transferTokens(
        @Valid @RequestBody request: TransferTokensRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<TransferTokensResponse>> {
        val authenticatedUsername = authentication.name

        logger.info {
            "User $authenticatedUsername transferring ${request.amount} tokens " +
                    "from ${request.from} to ${request.to}"
        }

        // Validar que usuário autenticado é o sender (from)
        validateUserAuthorization(authenticatedUsername, request.from, "transfer tokens")

        val result = contractService.transferTokens(request)

        logger.debug { "Transfer successful: ${result.transactionHash}" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = result
            )
        )
    }

    /**
     * Consultar saldo de tokens de um usuário
     *
     * GET /api/v1/contract/balance/{userId}
     * Rate limit: 30 requisições por minuto
     * Público (não requer autenticação)
     */
    @GetMapping("/balance/{userId}")
    //@RateLimit(maxRequests = 30, durationSeconds = 60)
    fun getTokenBalance(
        @PathVariable userId: String
    ): ResponseEntity<ApiResponse<TokenBalanceResponse>> {
        logger.debug { "Token balance query for user: $userId" }

        val balance = contractService.getTokenBalance(userId)

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = balance
            )
        )
    }

    /**
     * Obter informações do contrato CoffeeToken
     *
     * GET /api/v1/contract/info
     * Público (não requer autenticação)
     */
    @GetMapping("/info")
    fun getContractInfo(): ResponseEntity<Map<String, Any>> {
        logger.debug { "Contract info request" }

        val info = contractService.getContractInfo()
        return ResponseEntity.ok(info)
    }

    /**
     * Valida autorização do usuário para operação
     */
    private fun validateUserAuthorization(
        authenticatedUser: String,
        requestedUser: String,
        operation: String
    ) {
        if (authenticatedUser != requestedUser) {
            logger.warn {
                "Unauthorized: $authenticatedUser attempted to $operation for $requestedUser"
            }
            throw ValidationException(
                mapOf("authorization" to "You can only $operation for your own account")
            )
        }
    }
}