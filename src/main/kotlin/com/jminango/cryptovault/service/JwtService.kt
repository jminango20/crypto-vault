package com.jminango.cryptovault.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

private val logger = KotlinLogging.logger {}

@Service
class JwtService(
    @Value("\${cryptovault.jwt.secret}") private val jwtSecret: String,
    @Value("\${cryptovault.jwt.expiration}") private val jwtExpiration: Long  // milliseconds
) {

    private val key: SecretKey

    init {
        require(jwtSecret.length >= 32) {
            "JWT secret must be at least 32 characters. Current length: ${jwtSecret.length}"
        }
        key = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
        logger.info { "JwtService initialized" }
    }

    fun generateToken(username: String): String {
        val now = Date()
        val expiry = Date(now.time + jwtExpiration)
        logger.debug { "Generating JWT token for user: $username (expires: $expiry)" }
        return Jwts.builder()
            .subject(username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun getUsernameFromToken(token: String): String = getAllClaims(token).subject

    fun validateToken(token: String): Boolean {
        return try {
            getAllClaims(token)
            true
        } catch (e: Exception) {
            logger.warn { "Token validation failed: ${e.javaClass.simpleName}" }
            false
        }
    }

    fun isTokenExpired(token: String): Boolean = getAllClaims(token).expiration.before(Date())

    fun getExpirationInSeconds(): Long = jwtExpiration / 1000

    private fun getAllClaims(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
}
