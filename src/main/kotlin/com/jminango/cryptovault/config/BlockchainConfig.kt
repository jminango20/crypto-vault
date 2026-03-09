package com.jminango.cryptovault.config

import mu.KotlinLogging
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Configuration
class BlockchainConfig(
    @Value("\${cryptovault.blockchain.rpc.url}")
    private val rpcUrl: String
) {

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
        private val CREDENTIALS_REGEX = Regex("https?://([^:]+):([^@]+)@(.+)")
    }

    @Bean
    fun web3j(): Web3j {
        logger.info { "Initializing blockchain connection to: ${sanitizeUrl(rpcUrl)}" }

        return try {
            val credentials = tryExtractCredentials(rpcUrl)
            val (cleanUrl, okHttpClient) = if (credentials != null) {
                val (url, username, password) = credentials
                logger.debug { "Authentication configured for user: $username" }
                url to buildHttpClient(username, password)
            } else {
                logger.debug { "No credentials in URL, using unauthenticated connection" }
                rpcUrl to OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
            }

            logger.debug { "Using RPC endpoint: $cleanUrl" }
            val web3j = Web3j.build(HttpService(cleanUrl, okHttpClient))

            testConnection(web3j)

            logger.info { "Blockchain connection established successfully" }
            web3j

        } catch (e: Exception) {
            logger.error(e) { "Failed to establish blockchain connection" }
            throw RuntimeException("Blockchain connection failed: ${e.message}", e)
        }
    }

    /**
     * Constrói cliente HTTP com autenticação e timeouts
     */
    private fun buildHttpClient(username: String, password: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Testa conectividade com blockchain (non-fatal — logs warning if unreachable)
     */
    private fun testConnection(web3j: Web3j) {
        try {
            val version = web3j.web3ClientVersion().send()
            logger.info { "Connected to blockchain client: ${version.web3ClientVersion}" }

            val blockNumber = web3j.ethBlockNumber().send().blockNumber
            logger.info { "Current block number: $blockNumber" }

        } catch (e: Exception) {
            logger.warn { "Blockchain node unreachable at startup: ${e.message}. Blockchain features will be unavailable." }
        }
    }

    /**
     * Extracts credentials from URL in format https://username:password@host/path.
     * Returns null if URL has no embedded credentials.
     */
    private fun tryExtractCredentials(url: String): Triple<String, String, String>? {
        val matchResult = CREDENTIALS_REGEX.find(url) ?: return null
        val (username, password, host) = matchResult.destructured
        val scheme = if (url.startsWith("https")) "https" else "http"
        return Triple("$scheme://$host", username, password)
    }

    /**
     * Remove credenciais da URL para logging seguro
     */
    private fun sanitizeUrl(url: String): String {
        return url.replace(Regex("://[^:]+:[^@]+@"), "://***:***@")
    }
}