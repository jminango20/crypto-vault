package com.jminango.cryptovault.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "wallets")
data class Wallet(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: String,

    @Column(nullable = false, unique = true)
    val address: String,

    @Column(name = "wallet_index", nullable = false)
    val walletIndex: Int = 0,

    @Column(name = "derivation_path", nullable = false)
    val derivationPath: String,

    @Column(name = "derivation_salt", nullable = false)
    val derivationSalt: String,

    @Column(nullable = false)
    val network: String = "Besu",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)


@Entity
@Table(name = "transactions")
data class Transaction(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,

    @Column(name = "tx_hash", unique = true, nullable = false)
    val txHash: String,

    @Column(name = "from_address", nullable = false)
    val fromAddress: String,

    @Column(name = "to_address", nullable = false)
    val toAddress: String,

    @Column(nullable = false)
    val data: String? = null,

    @Column(nullable = false)
    val status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)