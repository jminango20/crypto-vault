package com.jminango.cryptovault.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Configuration
class RateLimitConfig {

    // Armazena um bucket para cada cliente (IP)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    /**
     * Obtém ou cria um bucket para um cliente específico
     *
     * @param key Identificador do cliente (ex: "ip:192.168.1.1")
     * @param maxRequests Máximo de requisições permitidas
     * @param duration Janela de tempo
     */
    fun resolveBucket(
        key: String,
        maxRequests: Long = 10,
        duration: Duration = Duration.ofMinutes(1)
    ): Bucket {

        return buckets.computeIfAbsent(key) {
            createNewBucket(maxRequests, duration)
        }
    }

    private fun createNewBucket(maxRequests: Long, duration: Duration): Bucket {

        // Definir a "banda" = regra do bucket
        val bandwidth = Bandwidth.classic(
            maxRequests,                        // Capacidade máxima
            Refill.intervally(maxRequests, duration) // Como reabastecer
        )

        logger.debug {
            "Criando bucket: $maxRequests requisições a cada ${duration.toMinutes()} minuto(s)"
        }

        // Construir o bucket
        return Bucket.builder()
            .addLimit(bandwidth)
            .build()
    }

    /**
     * Limpa buckets para liberar memória
     */
    fun cleanupBuckets() {
        val sizeBefore = buckets.size
        buckets.clear()
        logger.info { "Buckets limpos: $sizeBefore -> 0" }
    }
}