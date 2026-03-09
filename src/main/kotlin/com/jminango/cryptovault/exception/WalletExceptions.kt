package com.jminango.cryptovault.exception

/**
 * Exception base para todas as exceções customizadas do CryptoVault
 */
sealed class CryptoVaultException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// ==================== WALLET EXCEPTIONS ====================

/**
 * Lançada quando se tenta criar uma carteira que já existe
 */
class WalletAlreadyExistsException(userId: String) :
    CryptoVaultException("Wallet already exists for user: $userId")

/**
 * Lançada quando uma carteira não é encontrada
 */
class WalletNotFoundException(identifier: String) :
    CryptoVaultException("Wallet not found: $identifier")

/**
 * Lançada quando uma carteira é inválida
 */
class InvalidWalletException(reason: String) :
    CryptoVaultException("Invalid wallet: $reason")

// ==================== TRANSACTION EXCEPTIONS ====================

/**
 * Lançada quando falha ao assinar uma transação
 */
class TransactionSigningException(reason: String, cause: Throwable? = null) :
    CryptoVaultException("Error to sign transaction: $reason", cause)

/**
 * Lançada quando uma transação é inválida
 */
class InvalidTransactionException(reason: String) :
    CryptoVaultException("Invalid transaction: $reason")

// ==================== CRYPTOGRAPHY EXCEPTIONS ====================

/**
 * Lançada quando ocorre erro criptográfico
 */
class CryptographyException(reason: String, cause: Throwable? = null) :
    CryptoVaultException("Cryptography error: $reason", cause)

/**
 * Lançada quando falha na derivação de chaves HD
 */
class KeyDerivationException(reason: String, cause: Throwable? = null) :
    CryptoVaultException("Error to derive key: $reason", cause)

// ==================== VALIDATION EXCEPTIONS ====================

/**
 * Lançada quando validação de dados falha
 */
class ValidationException(val errors: Map<String, String>) :
    CryptoVaultException("Validation error: ${errors.values.joinToString(", ")}")