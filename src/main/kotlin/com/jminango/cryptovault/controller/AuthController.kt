package com.jminango.cryptovault.controller

import com.jminango.cryptovault.annotation.RateLimit
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
class AuthController(
    private val authService: AuthService
) {

    /**
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    @RateLimit(maxRequests = 5, durationSeconds = 60)
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {

        logger.info { "Login request received: ${request.username}" }

        val response = authService.login(request)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = response
        ))
    }

    /**
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    @RateLimit(maxRequests = 3, durationSeconds = 60)
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<ApiResponse<RegisterResponse>> {

        logger.info { "Register request received: ${request.username}" }

        val response = authService.register(request)

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse(
                success = true,
                data = response
            ))
    }

    /**
     * GET /api/v1/auth/validate
     * Header: Authorization: Bearer <token>
     */
    @GetMapping("/validate")
    fun validateToken(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {

        // Extrair token do header "Bearer <token>"
        val token = authHeader.removePrefix("Bearer ").trim()

        val isValid = authService.validateToken(token)

        return if (isValid) {
            val username = authService.getUsernameFromToken(token)
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = mapOf(
                    "valid" to true,
                    "username" to username
                )
            ))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse(
                    success = false,
                    data = mapOf("valid" to false),
                    error = "Invalid token or token expired"
                ))
        }
    }
}