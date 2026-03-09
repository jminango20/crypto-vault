package com.jminango.cryptovault.exception

import com.jminango.cryptovault.dto.ApiResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Centralizes exception handling for all REST controllers.
 *
 * Cryptographic and internal errors return a generic 500 message to avoid leaking
 * implementation details to callers. Validation and not-found errors include specifics.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException::class)
    fun handleWalletNotFound(ex: WalletNotFoundException, request: WebRequest) =
        errorResponse(HttpStatus.NOT_FOUND, ex.message)

    @ExceptionHandler(WalletAlreadyExistsException::class)
    fun handleWalletAlreadyExists(ex: WalletAlreadyExistsException, request: WebRequest) =
        errorResponse(HttpStatus.CONFLICT, ex.message)

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException, request: WebRequest): ResponseEntity<ApiResponse<Map<String, String>>> {
        logger.warn { "Validation error: ${ex.errors}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse(success = false, data = ex.errors, error = "Validation failed", timestamp = LocalDateTime.now())
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<ApiResponse<Map<String, String>>> {
        val errors = ex.bindingResult.allErrors.associate { error ->
            val fieldName = (error as? FieldError)?.field ?: "unknown"
            fieldName to (error.defaultMessage ?: "Invalid value")
        }
        logger.warn { "Bean validation failed: $errors" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse(success = false, data = errors, error = "Validation failed", timestamp = LocalDateTime.now())
        )
    }

    @ExceptionHandler(InvalidTransactionException::class)
    fun handleInvalidTransaction(ex: InvalidTransactionException, request: WebRequest) =
        errorResponse(HttpStatus.BAD_REQUEST, ex.message)

    @ExceptionHandler(TransactionSigningException::class)
    fun handleTransactionSigning(ex: TransactionSigningException, request: WebRequest): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Transaction signing failed" }
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process transaction")
    }

    // Cryptographic failures return a generic message — internal details must not reach clients.
    @ExceptionHandler(CryptographyException::class, KeyDerivationException::class)
    fun handleCryptography(ex: CryptoVaultException, request: WebRequest): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Cryptography error" }
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Unhandled exception: ${ex.javaClass.simpleName}" }
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }

    private fun errorResponse(status: HttpStatus, message: String?): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "${status.value()} — $message" }
        return ResponseEntity.status(status).body(
            ApiResponse(success = false, error = message, timestamp = LocalDateTime.now())
        )
    }
}
