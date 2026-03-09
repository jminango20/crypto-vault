package com.jminango.cryptovault.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * In-memory rate limit bucket registry backed by Bucket4j.
 *
 * One bucket is maintained per client key (user ID or IP). Buckets inactive for more
 * than one hour are evicted by a scheduled cleanup to prevent unbounded memory growth.
 *
 * Note: this implementation is single-instance only. For horizontally scaled deployments,
 * replace the in-memory map with a distributed store (e.g., Redis + Bucket4j JCache).
 */
@Configuration
class RateLimitConfig {

    companion object {
        private const val DEFAULT_MAX_REQUESTS = 10L
        private val DEFAULT_DURATION: Duration = Duration.ofMinutes(1)
        private const val CLEANUP_CRON = "0 0 * * * *"  // every hour
    }

    private val buckets = ConcurrentHashMap<String, BucketInfo>()

    fun resolveBucket(
        key: String,
        maxRequests: Long = DEFAULT_MAX_REQUESTS,
        duration: Duration = DEFAULT_DURATION
    ): Bucket {
        return buckets.computeIfAbsent(key) {
            BucketInfo(createBucket(maxRequests, duration))
        }.also { it.updateLastAccess() }.bucket
    }

    private fun createBucket(maxRequests: Long, duration: Duration): Bucket =
        Bucket.builder()
            .addLimit(Bandwidth.classic(maxRequests, Refill.intervally(maxRequests, duration)))
            .build()

    @Scheduled(cron = CLEANUP_CRON)
    fun cleanupInactiveBuckets() {
        val threshold = System.currentTimeMillis() - Duration.ofHours(1).toMillis()
        val before = buckets.size
        buckets.entries.removeIf { (_, info) -> info.lastAccessTime < threshold }
        val removed = before - buckets.size
        if (removed > 0) logger.info { "Evicted $removed inactive rate limit buckets ($before → ${buckets.size})" }
    }

    fun clearAllBuckets() {
        val before = buckets.size
        buckets.clear()
        logger.info { "All rate limit buckets cleared ($before → 0)" }
    }

    fun getStatistics(): Map<String, Any> = mapOf(
        "totalBuckets" to buckets.size,
        "activeBuckets" to buckets.count {
            it.value.lastAccessTime > System.currentTimeMillis() - Duration.ofMinutes(5).toMillis()
        }
    )

    private data class BucketInfo(
        val bucket: Bucket,
        var lastAccessTime: Long = System.currentTimeMillis()
    ) {
        fun updateLastAccess() { lastAccessTime = System.currentTimeMillis() }
    }
}
