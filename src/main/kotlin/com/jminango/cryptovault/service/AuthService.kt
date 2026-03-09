package com.jminango.cryptovault.service

import com.jminango.cryptovault.dto.LoginRequest
import com.jminango.cryptovault.dto.LoginResponse
import com.jminango.cryptovault.dto.RegisterRequest
import com.jminango.cryptovault.dto.RegisterResponse
import com.jminango.cryptovault.exception.ValidationException
import com.jminango.cryptovault.model.User
import com.jminango.cryptovault.repository.UserRepository
import mu.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    companion object {
        // A pre-hashed dummy password ensures BCrypt runs even for unknown usernames,
        // preventing timing-based username enumeration attacks.
        private const val DUMMY_PASSWORD = "dummy-password-for-timing-attack-prevention"
    }

    private val dummyPasswordHash by lazy { passwordEncoder.encode(DUMMY_PASSWORD) }

    fun login(request: LoginRequest): LoginResponse {
        logger.info { "Login attempt for user: ${request.username}" }

        val user = userRepository.findByUsername(request.username).orElse(null)
        val passwordHash = user?.password ?: dummyPasswordHash
        val isValid = user != null && passwordEncoder.matches(request.password, passwordHash) && user.enabled

        if (!isValid) {
            logger.warn { "Failed login attempt for username: ${request.username}" }
            throw ValidationException(mapOf("credentials" to "Invalid username or password"))
        }

        val token = jwtService.generateToken(user!!.username)
        logger.info { "Login successful for user: ${user.username}" }

        return LoginResponse(
            token = token,
            username = user.username,
            expiresIn = jwtService.getExpirationInSeconds()
        )
    }

    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        logger.info { "Registration attempt for user: ${request.username}" }

        if (userRepository.existsByUsername(request.username)) {
            logger.warn { "Username already exists: ${request.username}" }
            throw ValidationException(mapOf("username" to "Username already exists"))
        }

        if (request.email != null && userRepository.existsByEmail(request.email)) {
            logger.warn { "Email already exists: ${request.email}" }
            throw ValidationException(mapOf("email" to "Email already exists"))
        }

        val user = User(
            username = request.username,
            password = passwordEncoder.encode(request.password),
            email = request.email,
            role = "USER",
            enabled = true
        )

        userRepository.save(user)
        logger.info { "User registered: ${request.username}" }

        return RegisterResponse(username = user.username, message = "User registered successfully")
    }

    fun validateToken(token: String): Boolean {
        return try {
            jwtService.validateToken(token) && !jwtService.isTokenExpired(token)
        } catch (e: Exception) {
            logger.warn { "Invalid token: ${e.message}" }
            false
        }
    }

    fun getUsernameFromToken(token: String): String = jwtService.getUsernameFromToken(token)
}
