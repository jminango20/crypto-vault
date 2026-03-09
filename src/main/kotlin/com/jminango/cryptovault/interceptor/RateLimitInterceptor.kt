package com.jminango.cryptovault.interceptor

import com.jminango.cryptovault.annotation.RateLimit
import com.jminango.cryptovault.config.RateLimitConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Enforces per-client rate limits on endpoints annotated with [@RateLimit][RateLimit].
 *
 * Clients are identified by the X-User-Id header when present (authenticated requests),
 * falling back to the originating IP address for unauthenticated traffic.
 * Throttled requests receive HTTP 429 with a Retry-After header.
 */
@Component
class RateLimitInterceptor(private val rateLimitConfig: RateLimitConfig) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        val rateLimit = handler.getMethodAnnotation(RateLimit::class.java) ?: return true

        val clientKey = getClientIdentifier(request)
        logger.debug { "Rate limit check for $clientKey on ${request.requestURI}" }

        val bucket = rateLimitConfig.resolveBucket(
            key = clientKey,
            maxRequests = rateLimit.maxRequests.toLong(),
            duration = Duration.ofSeconds(rateLimit.durationSeconds)
        )

        return if (bucket.tryConsume(1)) {
            response.addHeader("X-RateLimit-Limit", rateLimit.maxRequests.toString())
            response.addHeader("X-RateLimit-Remaining", bucket.availableTokens.toString())
            response.addHeader("X-RateLimit-Reset", rateLimit.durationSeconds.toString())
            true
        } else {
            logger.warn { "Rate limit exceeded for $clientKey on ${request.requestURI}" }
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json;charset=UTF-8"
            response.addHeader("Retry-After", rateLimit.durationSeconds.toString())
            response.writer.write("""{"success":false,"error":"Rate limit exceeded. Retry in ${rateLimit.durationSeconds} seconds.","retryAfter":${rateLimit.durationSeconds}}""")
            false
        }
    }

    private fun getClientIdentifier(request: HttpServletRequest): String {
        val userId = request.getHeader("X-User-Id")
        if (!userId.isNullOrBlank()) return "user:$userId"
        return "ip:${getClientIP(request)}"
    }

    private fun getClientIP(request: HttpServletRequest): String {
        // X-Forwarded-For contains "client, proxy1, proxy2" — take the first entry (real client).
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank())
            xForwardedFor.split(",").firstOrNull()?.trim() ?: request.remoteAddr
        else
            request.remoteAddr ?: "unknown"
    }
}
