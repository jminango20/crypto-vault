package com.jminango.cryptovault.security

import com.jminango.cryptovault.service.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val logger = KotlinLogging.logger {}

/**
 * Extracts and validates the JWT from every incoming request's Authorization header.
 * On a valid token, populates the Spring Security context so downstream handlers
 * can access the authenticated principal.
 *
 * Failures are intentionally non-fatal: the filter chain continues and Spring
 * Security rejects the request at the authorization layer if authentication is required.
 */
@Component
class JwtAuthenticationFilter(
    private val authService: AuthService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val AUTHORIZATION_HEADER = "Authorization"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = extractJwt(request)
            if (jwt != null && authService.validateToken(jwt)) {
                authenticateUser(jwt, request)
            }
        } catch (e: Exception) {
            logger.debug { "JWT processing failed for ${request.requestURI}: ${e.message}" }
        }

        filterChain.doFilter(request, response)
    }

    private fun authenticateUser(jwt: String, request: HttpServletRequest) {
        val username = authService.getUsernameFromToken(jwt)
        val userDetails = userDetailsService.loadUserByUsername(username)

        val authentication = UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.authorities
        ).also { it.details = WebAuthenticationDetailsSource().buildDetails(request) }

        SecurityContextHolder.getContext().authentication = authentication
        logger.debug { "Authenticated user: $username (authorities: ${userDetails.authorities.joinToString()})" }
    }

    private fun extractJwt(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(AUTHORIZATION_HEADER)
        return if (bearerToken?.startsWith(BEARER_PREFIX) == true)
            bearerToken.substring(BEARER_PREFIX.length).trim()
        else null
    }
}
