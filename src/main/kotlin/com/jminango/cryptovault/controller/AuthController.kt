package com.jminango.cryptovault.controller

import com.jminango.cryptovault.dto.*
import com.jminango.cryptovault.service.AuthService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = ["*"])
class AuthController(private val authService: AuthService) {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        logger.info { "Login attempt for user: ${request.username}" }
        val response = authService.login(request)
        return ResponseEntity.ok(ApiResponse(success = true, data = response))
    }

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<RegisterResponse>> {
        logger.info { "Registration attempt for user: ${request.username}" }
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(success = true, data = response))
    }

    /**
     * Validates a JWT and returns the associated username.
     * Clients can use this to verify token liveness before initiating custody operations.
     */
    @GetMapping("/validate")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val token = authHeader.removePrefix(BEARER_PREFIX).trim().also {
            require(it.isNotEmpty()) { "Token cannot be empty" }
        }

        val isValid = authService.validateToken(token)

        return if (isValid) {
            val username = authService.getUsernameFromToken(token)
            ResponseEntity.ok(ApiResponse(success = true, data = mapOf("valid" to true, "username" to username)))
        } else {
            logger.warn { "Invalid or expired token" }
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse(success = false, data = mapOf("valid" to false), error = "Invalid or expired token"))
        }
    }
}
