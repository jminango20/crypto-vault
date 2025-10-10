package com.jminango.cryptovault.utils

import com.jminango.cryptovault.service.JwtService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

fun main() {
    val encoder = BCryptPasswordEncoder()
    val hash = encoder.encode("password123")
    println("Hash: $hash")

    testJwt()
}

// Cole isso temporariamente em algum lugar para testar
fun testJwt() {
    val jwtService = JwtService(
        jwtSecret = "minha-chave-super-secreta-com-no-minimo-256-bits",
        jwtExpiration = 86400000
    )

    // Gerar token
    val token = jwtService.generateToken("testuser")
    println("Token gerado: $token")

    // Validar token
    val isValid = jwtService.validateToken(token)
    println("Token válido? $isValid")

    // Extrair username
    val username = jwtService.getUsernameFromToken(token)
    println("Username do token: $username")
}