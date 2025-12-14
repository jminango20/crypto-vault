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

    private val dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-attack-prevention")

    fun login(request: LoginRequest): LoginResponse {
        logger.info { "Tentativa de login: ${request.username}" }

        // Buscar usuário no banco
        val user = userRepository.findByUsername(request.username).orElse(null)

        val passwordHash = user?.password ?: dummyPasswordHash

        val passwordMatches = passwordEncoder.matches(request.password, passwordHash)

        val isValid = user != null && passwordMatches && user.enabled

        if (!isValid) {
            logger.warn { "Failed login attempt for username: ${request.username}" }

            throw ValidationException(
                mapOf("credentials" to "Invalid username or password")
            )
        }

        // Gerar token JWT
        val token = jwtService.generateToken(user!!.username)

        logger.info { "Login successful for user: ${user.username}" }

        // Retornar token
        return LoginResponse(
            token = token,
            username = user.username,
            expiresIn = jwtService.getExpirationInSeconds()
        )
    }

    /**
     * Registra um novo usuário
     */
    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        logger.info { "Register request: ${request.username}" }

        // 1. Validar se username já existe
        if (userRepository.existsByUsername(request.username)) {
            logger.warn { "Username already exists: ${request.username}" }
            throw ValidationException(mapOf("username" to "Username already exists"))
        }

        // 2. Validar se email já existe
        if (request.email != null && userRepository.existsByEmail(request.email)) {
            logger.warn { "Email already exists: ${request.email}" }
            throw ValidationException(mapOf("email" to "Email already exists"))
        }

        // 3. Criptografar senha com BCrypt
        val hashedPassword = passwordEncoder.encode(request.password)

        logger.debug { "Password cryptographed: ${request.username}" }

        // 4. Criar usuário
        val user = User(
            username = request.username,
            password = hashedPassword,
            email = request.email,
            role = "USER",
            enabled = true
        )

        // 5. Salvar no banco
        userRepository.save(user)

        logger.info { "User registered: ${request.username}" }

        return RegisterResponse(
            username = user.username,
            message = "User registered successfully"
        )
    }

    fun validateToken(token: String): Boolean {
        return try {
            jwtService.validateToken(token) && !jwtService.isTokenExpired(token)
        } catch (e: Exception) {
            logger.warn { "Invalid token: ${e.message}" }
            false
        }
    }

    fun getUsernameFromToken(token: String): String {
        return jwtService.getUsernameFromToken(token)
    }
}