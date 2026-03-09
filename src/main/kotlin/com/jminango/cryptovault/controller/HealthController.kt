package com.jminango.cryptovault.controller

import com.jminango.cryptovault.service.BlockchainService
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Controller para health checks e informações do sistema
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
class HealthController(
    private val blockchainService: BlockchainService,
    @Value("\${cryptovault.signing.chain.id}") private val chainId: Long,
    @Value("\${cryptovault.version}") private val version: String
) {

    /**
     * Health check básico
     *
     * GET /api/v1/health
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "CryptoVault",
                "version" to version,
                "timestamp" to Instant.now().toString()
            )
        )
    }

    /**
     * Status detalhado da conexão blockchain
     *
     * GET /api/v1/blockchain/status
     */
    @GetMapping("/blockchain/status")
    fun getBlockchainStatus(): ResponseEntity<Map<String, Any>> {
        return try {
            val web3ClientVersion = blockchainService.getClientVersion()
            val latestBlock = blockchainService.getLatestBlockNumber()

            logger.debug {
                "Blockchain status check - Block: $latestBlock, Client: $web3ClientVersion"
            }

            ResponseEntity.ok(
                mapOf(
                    "status" to "CONNECTED",
                    "client" to web3ClientVersion,
                    "latestBlock" to latestBlock.toString(),
                    "chainId" to chainId.toString(),
                    "timestamp" to Instant.now().toString()
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get blockchain status" }

            ResponseEntity.status(500).body(
                mapOf(
                    "status" to "ERROR",
                    "error" to (e.message ?: "Unknown error"),
                    "timestamp" to Instant.now().toString()
                )
            )
        }
    }

    /**
     * Informações gerais do sistema
     *
     * GET /api/v1/info
     */
    @GetMapping("/info")
    fun getInfo(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "service" to "CryptoVault",
                "version" to version,
                "description" to "Secure HD Wallet custody for agricultural tokenization",
                "features" to listOf(
                    "HD Wallet derivation (BIP-44)",
                    "Transaction signing",
                    "Smart contract interaction",
                    "Multi-tenant support",
                    "Rate limiting",
                    "JWT authentication"
                ),
                "blockchain" to mapOf(
                    "chainId" to chainId.toString(),
                    "network" to "Kaleido"
                ),
                "timestamp" to Instant.now().toString()
            )
        )
    }
}