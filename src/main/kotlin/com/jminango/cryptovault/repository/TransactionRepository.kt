package com.jminango.cryptovault.repository

import com.jminango.cryptovault.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByWalletId(walletId: Long): List<Transaction>
    fun findByTxHash(txHash: String): Optional<Transaction>
    fun findByFromAddress(fromAddress: String): List<Transaction>
    fun findByToAddress(toAddress: String): List<Transaction>
}