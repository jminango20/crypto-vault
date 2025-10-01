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

@Component
class RateLimitInterceptor(
    private val rateLimitConfig: RateLimitConfig
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {

        if (handler !is HandlerMethod) {
            logger.debug { "Handler não é um método, ignorando rate limit" }
            return true
        }

        val rateLimit = handler.getMethodAnnotation(RateLimit::class.java)

        if (rateLimit == null) {
            logger.debug { "Método sem @RateLimit, ignorando" }
            return true
        }

        val clientKey = getClientIdentifier(request)

        logger.debug {
            "Verificando rate limit para $clientKey: " +
                    "${rateLimit.maxRequests} req/${rateLimit.durationSeconds}s"
        }

        val bucket = rateLimitConfig.resolveBucket(
            key = clientKey,
            maxRequests = rateLimit.maxRequests.toLong(),
            duration = Duration.ofSeconds(rateLimit.durationSeconds)
        )

        if (bucket.tryConsume(1)) {
            val tokensLeft = bucket.availableTokens

            logger.debug { "Requisição permitida para $clientKey (tokens restantes: $tokensLeft)" }

            response.addHeader("X-RateLimit-Limit", rateLimit.maxRequests.toString())
            response.addHeader("X-RateLimit-Remaining", tokensLeft.toString())

            return true
        } else {
            logger.warn { "Rate limit excedido para $clientKey em ${request.requestURI}" }

            // Retornar erro 429 (Too Many Requests)
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""
                {
                    "success": false,
                    "error": "Muitas requisições. Tente novamente em ${rateLimit.durationSeconds} segundos.",
                    "retryAfter": ${rateLimit.durationSeconds}
                }
            """.trimIndent())

            return false
        }
    }

    /**
     * Identifica o cliente pela requisição
     */
    private fun getClientIdentifier(request: HttpServletRequest): String {

        val userId = request.getHeader("X-User-Id")
        if (!userId.isNullOrBlank()) {
            return "user:$userId"
        }

        val ip = getClientIP(request)
        return "ip:$ip"
    }

    /**
     * Obtém o IP real do cliente (considerando proxies/load balancers)
     */
    private fun getClientIP(request: HttpServletRequest): String {

        // Tentar pegar o IP do header X-Forwarded-For (quando há proxy)
        val xForwardedFor = request.getHeader("X-Forwarded-For")

        return if (!xForwardedFor.isNullOrBlank()) {
            // X-Forwarded-For pode ter múltiplos IPs: "client, proxy1, proxy2"
            // Pegamos o primeiro (cliente real)
            xForwardedFor.split(",")[0].trim()
        } else {
            // Sem proxy, pegar IP direto
            request.remoteAddr ?: "unknown"
        }
    }
}