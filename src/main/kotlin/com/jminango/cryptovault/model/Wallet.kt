package com.jminango.cryptovault.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "wallets",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_address", columnList = "address")
    ]
)
data class Wallet(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    val userId: String,

    @Column(nullable = false, unique = true, length = 42)  // 0x + 40 hex chars
    val address: String,

    @Column(name = "wallet_index", nullable = false)
    val walletIndex: Int = 0,

    // AES-256-GCM encrypted derivation index; userId is bound as AAD.
    // Contains only the integer index needed to re-derive the key — no private key material.
    @Column(name = "derivation_path", nullable = false, length = 500)
    val derivationPath: String,

    @Column(nullable = false, length = 20)
    val network: String = "Kaleido",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(
    name = "transactions",
    indexes = [
        Index(name = "idx_wallet_id", columnList = "wallet_id"),
        Index(name = "idx_tx_hash", columnList = "tx_hash"),
        Index(name = "idx_from_address", columnList = "from_address"),
        Index(name = "idx_status", columnList = "status"),
        Index(name = "idx_created_at", columnList = "created_at")
    ]
)
data class Transaction(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,

    @Column(name = "tx_hash", unique = true, nullable = false, length = 66)  // 0x + 64 hex
    val txHash: String,

    @Column(name = "from_address", nullable = false, length = 42)
    val fromAddress: String,

    @Column(name = "to_address", nullable = false, length = 42)
    val toAddress: String,

    @Column(columnDefinition = "TEXT")
    val data: String? = null,

    @Column(nullable = false, length = 20)
    val status: String = "PENDING",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
