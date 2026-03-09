package com.jminango.cryptovault.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request de login
 */
data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    val password: String
)

/**
 * Response de login com token JWT
 */
data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val username: String,
    val expiresIn: Long  // Segundos até expirar
)

/**
 * Request de registro de novo usuário
 */
data class RegisterRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    val password: String,

    @field:Email(message = "Email must be valid")
    val email: String? = null
)

/**
 * Response de registro
 */
data class RegisterResponse(
    val username: String,
    val message: String = "User registered successfully"
)