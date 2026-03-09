package com.jminango.cryptovault.service

import com.jminango.cryptovault.config.MasterSeed
import com.jminango.cryptovault.exception.KeyDerivationException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.web3j.crypto.*
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

/**
 * Derives BIP-44 HD wallet key pairs from the single global master seed.
 *
 * All user wallets share the same root: the master seed is used directly as the
 * BIP-32 root seed, and each user is identified by a unique sequential index.
 * Derivation path: m/44'/60'/0'/0/i (BIP-44, Ethereum, external chain).
 *
 * Private keys are never stored — they are re-derived on demand from the master
 * seed and the sequential index, then discarded immediately after use.
 */
@Service
class CryptoService(
    private val masterSeed: MasterSeed,
    @Value("\${cryptovault.security.max-derivation-attempts:10}")
    private val maxDerivationAttempts: Int
) {

    companion object {
        // Upper bound of the secp256k1 curve order. Private keys outside [1, N-1] are invalid.
        private val SECP256K1_N = BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
            16
        )
    }

    /**
     * Derives the BIP-44 EC key pair for the given sequential wallet index.
     *
     * The master seed is used as the BIP-32 root seed, so a single 256-bit seed
     * recovers every user wallet by replaying the same derivation with the stored index.
     * Retries with the next index if the derived key falls outside the valid secp256k1 range
     * (this is a BIP-32 edge case with probability < 2^-127 per attempt).
     */
    fun derivePrivateKeyHD(walletIndex: Int): ECKeyPair {
        return deriveWithRetry(walletIndex, 0)
    }

    private fun deriveWithRetry(walletIndex: Int, attemptCount: Int): ECKeyPair {
        if (attemptCount >= maxDerivationAttempts) {
            logger.error { "Max derivation attempts ($maxDerivationAttempts) exceeded at index: $walletIndex" }
            throw KeyDerivationException(
                "Failed to derive valid key after $maxDerivationAttempts attempts at index: $walletIndex"
            )
        }

        try {
            val masterKeyPair = Bip32ECKeyPair.generateKeyPair(masterSeed.getBytes())

            // BIP-44 path: m/44'/60'/0'/0/walletIndex
            //   44' — BIP-44 multi-coin standard (hardened)
            //   60' — Ethereum coin type (hardened)
            //    0' — primary account (hardened)
            //    0  — external chain
            //    i  — sequential address index
            // Hardened levels (') prevent child-key compromise from revealing parent keys.
            val derivationPath = intArrayOf(
                44 or Bip32ECKeyPair.HARDENED_BIT,
                60 or Bip32ECKeyPair.HARDENED_BIT,
                0  or Bip32ECKeyPair.HARDENED_BIT,
                0,
                walletIndex
            )

            val childKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, derivationPath)

            if (!isValidPrivateKey(childKeyPair.privateKey)) {
                logger.warn {
                    "Invalid key at index $walletIndex " +
                    "(attempt ${attemptCount + 1}/$maxDerivationAttempts), retrying with next index"
                }
                return deriveWithRetry(walletIndex + 1, attemptCount + 1)
            }

            logger.debug { "HD key derived — index: $walletIndex" }
            return childKeyPair

        } catch (e: KeyDerivationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during HD key derivation at index: $walletIndex" }
            throw KeyDerivationException("HD derivation failed unexpectedly", e)
        }
    }

    private fun isValidPrivateKey(privateKey: BigInteger): Boolean =
        privateKey > BigInteger.ZERO && privateKey < SECP256K1_N
}
