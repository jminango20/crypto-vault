package com.jminango.cryptovault.annotation

/**
 * Anotação para aplicar rate limiting em métodos do controller
 *
 * Exemplo de uso:
 * @RateLimit(maxRequests = 5, durationSeconds = 60)
 * fun createWallet(...) { ... }
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    val maxRequests: Int = 10,
    val durationSeconds: Long = 60
)