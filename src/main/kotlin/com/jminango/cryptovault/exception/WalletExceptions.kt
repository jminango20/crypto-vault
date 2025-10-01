package com.jminango.cryptovault.exception

sealed class CryptoVaultException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exceções relacionadas a Wallet
 */
class WalletAlreadyExistsException(userId: String) :
    CryptoVaultException("Wallet already exists for user: $userId")

class WalletNotFoundException(identifier: String) :
    CryptoVaultException("Wallet not found: $identifier")

class InvalidWalletException(reason: String) :
    CryptoVaultException("Invalid wallet: $reason")

/**
 * Exceções relacionadas a Transações
 */
class TransactionSigningException(reason: String, cause: Throwable? = null) :
    CryptoVaultException("Error to sign transaction: $reason", cause)

class InvalidTransactionException(reason: String) :
    CryptoVaultException("Invalid transaction: $reason")

/**
 * Exceções relacionadas a Criptografia
 */
class CryptographyException(reason: String, cause: Throwable? = null) :
    CryptoVaultException("Cryptography error: $reason", cause)

class KeyDerivationException(reason: String, cause: Throwable? = null) :
    CryptoVaultException("Error to derive key: $reason", cause)

/**
 * Exceções de validação
 */
class ValidationException(val errors: Map<String, String>) :
    CryptoVaultException("Validation error: ${errors.values.joinToString(", ")}")