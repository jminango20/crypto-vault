package com.jminango.cryptovault.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

/**
 * Retorna o token
 */
data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val username: String,
    val expiresIn: Long  // Segundos até expirar
)

data class RegisterRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String,

    val email: String? = null
)


data class RegisterResponse(
    val username: String,
    val message: String = "User registered successfully"
)