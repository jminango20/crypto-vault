package com.jminango.cryptovault.exception

import com.jminango.cryptovault.dto.ApiResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Tratamento global de exceções
 */
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException::class)
    fun handleWalletNotFound(
        ex: WalletNotFoundException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.warn { "Wallet not found: ${ex.message}" }

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse(
                success = false,
                error = ex.message,
                timestamp = LocalDateTime.now()
            ))
    }

    @ExceptionHandler(WalletAlreadyExistsException::class)
    fun handleWalletAlreadyExists(
        ex: WalletAlreadyExistsException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.warn { "Wallet already exists: ${ex.message}" }

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse(
                success = false,
                error = ex.message,
                timestamp = LocalDateTime.now()
            ))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(
        ex: ValidationException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Map<String, String>>> {

        logger.warn { "Validation error: ${ex.errors}" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse(
                success = false,
                data = ex.errors,
                error = "Validation error",
                timestamp = LocalDateTime.now()
            ))
    }

    @ExceptionHandler(InvalidTransactionException::class)
    fun handleInvalidTransaction(
        ex: InvalidTransactionException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.warn { "Invalid transaction: ${ex.message}" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse(
                success = false,
                error = ex.message,
                timestamp = LocalDateTime.now()
            ))
    }

    @ExceptionHandler(TransactionSigningException::class)
    fun handleTransactionSigning(
        ex: TransactionSigningException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.error(ex) { "Error to sign transaction" }

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse(
                success = false,
                error = "Error to process transaction.",
                timestamp = LocalDateTime.now()
            ))
    }

    @ExceptionHandler(CryptographyException::class, KeyDerivationException::class)
    fun handleCryptography(
        ex: CryptoVaultException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.error(ex) { "Cryptography error: ${ex.message}" }

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse(
                success = false,
                error = "Internal server error.",
                timestamp = LocalDateTime.now()
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.error(ex) { "Error not handled: ${ex.javaClass.simpleName}" }

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse(
                success = false,
                error = "Internal server error.",
                timestamp = LocalDateTime.now()
            ))
    }
}