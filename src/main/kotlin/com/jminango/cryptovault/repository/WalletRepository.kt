package com.jminango.cryptovault.repository

import com.jminango.cryptovault.model.Wallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface WalletRepository: JpaRepository<Wallet, Long> {
    fun findByUserId(userId: String): Optional<Wallet>
    fun findByAddress(address: String): Optional<Wallet>
    fun existsByUserId(userId: String): Boolean

    // Returns -1 when no wallets exist, so the first wallet receives index 0.
    @Query("SELECT COALESCE(MAX(w.walletIndex), -1) FROM Wallet w")
    fun findMaxWalletIndex(): Int
}