# CryptoVault

A non-custodial HD wallet custody service built on Spring Boot, implementing BIP-32, BIP-39, and BIP-44 hierarchical deterministic key derivation. Private keys are **never stored** — they are re-derived on demand from a single master seed and a sequential derivation index, then discarded immediately after use.

This repository is the reference implementation accompanying the paper:

> **Zero-Trust Hierarchical Deterministic Wallet Architecture for
Institutional Custody**

---

## Overview

Traditional custody solutions require users to safeguard private keys and seed phrases, a practical barrier in contexts with limited digital literacy. CryptoVault addresses this by acting as a secure key management layer: external applications submit unsigned transactions, CryptoVault signs them and returns the signed payload, and the application broadcasts it to whichever blockchain network it targets.

The system is EVM-compatible by design. A single deployment works across Ethereum, Polygon, Hyperledger Besu, and any other EVM-compatible network without modifications to the signing logic.

### Key Properties

- A single 256-bit master seed derives all user wallets via `m/44'/60'/0'/0/i`
- No private key is ever written to disk or database — only an AES-256-GCM encrypted derivation index is persisted
- The `userId` is bound to the ciphertext as Additional Authenticated Data (AAD), preventing substitution attacks between user records
- A database breach exposes only encrypted derivation indexes — useless without the master seed and encryption key

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   External Application               │
│        (constructs unsigned tx, broadcasts result)   │
└──────────────────────┬──────────────────────────────┘
                       │ REST (JSON)
┌──────────────────────▼──────────────────────────────┐
│                  Backend Layer                       │
│                                                      │
│  AuthController   WalletController   TxController    │
│        │                │                 │          │
│  AuthService      WalletService    TransactionService│
│                         │                 │          │
│              CryptoService (HD derivation)           │
│              EncryptionService (AES-256-GCM)         │
│              MasterSeedLoader (startup, in-memory)   │
└──────────────────────┬──────────────────────────────┘
                       │ JPA
┌──────────────────────▼──────────────────────────────┐
│               Persistence Layer                      │
│  users(id, username, password_hash)                  │
│  wallets(user_id, address, encrypted_derivation_idx) │
│  transactions(wallet_id, tx_hash, status)            │
└─────────────────────────────────────────────────────┘
```

### Derivation Path

```
Master Seed (256-bit)
    └── m / 44' / 60' / 0' / 0 / i
              │      │     │   │   └─ sequential user index (stored encrypted)
              │      │     │   └───── external chain (non-hardened)
              │      │     └───────── account 0 (hardened)
              │      └─────────────── Ethereum coin type (hardened)
              └────────────────────── BIP-44 purpose (hardened)
```

Hardened levels (`'`) use HMAC-SHA512 in a way that prevents an attacker from recovering parent keys even with full access to a child key.

---

## Technology Stack

| Component      | Technology              | Version |
|----------------|-------------------------|---------|
| Language       | Kotlin                  | 1.9.21  |
| Framework      | Spring Boot             | 3.2.1   |
| JVM            | Java                    | 21      |
| Database       | PostgreSQL              | 15      |
| Cryptography   | Bouncy Castle + Web3j   | 1.70 / 4.10.3 |
| Authentication | JWT (HS256) via JJWT    | 0.12.3  |
| Rate Limiting  | Bucket4j + Caffeine     | 8.7.0   |
| Testing        | JUnit 5 + Mockito-Kotlin | —      |

---

## Prerequisites

- Java 21
- Docker and Docker Compose (for PostgreSQL)
- Gradle 8 (or use the included `./gradlew` wrapper)

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/jminango20/crypto-vault.git
cd crypto-vault
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` with your values. The minimum required fields are:

```env
DATABASE_URL=jdbc:postgresql://localhost:5433/cryptovault
DATABASE_USER=cryptovault
DATABASE_PASSWORD=your_password_here

JWT_SECRET=your-jwt-secret-minimum-32-characters

# Master seed: use BASE64:<b64> for dev, ENC:<ciphertext> for production
MASTER_SEED=BASE64:your-base64-encoded-seed
MASTER_PASSWORD=your-master-password

ENCRYPTION_PASSWORD=your-encryption-password

BLOCKCHAIN_RPC_URL=https://your-node-rpc-url
BLOCKCHAIN_CHAIN_ID=1337
```

> **Security note:** The master seed is the most critical secret in the system. In production it must be stored in an HSM or secrets manager and loaded via `ENC:<ciphertext>` format. Never commit a real seed to version control.

### 3. Start the database

```bash
docker compose up -d
```

### 4. Run the application

```bash
./gradlew bootRun
```

The API will be available at `http://localhost:8080`.

---

## REST API

All endpoints accept and return JSON. Protected endpoints require a JWT in the `Authorization: Bearer <token>` header.

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | None | Create a user account |
| `POST` | `/api/v1/auth/login` | None | Authenticate and receive a JWT |
| `GET`  | `/api/v1/auth/validate` | Bearer | Validate a token and return the username |

**Register**
```json
POST /api/v1/auth/register
{
  "username": "farmer01",
  "password": "securepassword"
}
```

**Login**
```json
POST /api/v1/auth/login
{
  "username": "farmer01",
  "password": "securepassword"
}
```
```json
{
  "success": true,
  "data": {
    "token": "<jwt>",
    "username": "farmer01"
  }
}
```

---

### Wallet Management

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/wallet/create` | Bearer | Derive and register an HD wallet for the authenticated user |
| `GET`  | `/api/v1/wallet/{userId}` | Bearer | Retrieve wallet metadata |
| `GET`  | `/api/v1/wallet/{userId}/address` | Bearer | Retrieve the Ethereum address |
| `GET`  | `/api/v1/wallet/{userId}/balance/native` | Bearer | Retrieve the native token balance |

**Create wallet**
```json
POST /api/v1/wallet/create
Authorization: Bearer <token>
{
  "userId": "farmer01",
  "network": "Besu"
}
```
```json
{
  "success": true,
  "data": {
    "userId": "farmer01",
    "address": "0xabc123...",
    "network": "Besu",
    "createdAt": "2025-01-01T00:00:00"
  }
}
```

One wallet per user is enforced. A user can only create and access their own wallet.

---

### Transaction Signing

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/transaction/sign` | Bearer | Sign a transaction and return the signed hex payload |
| `POST` | `/api/v1/transaction/send` | Bearer | Sign and broadcast a transaction to the configured network |
| `GET`  | `/api/v1/transaction/history/{userId}` | Bearer | Retrieve transaction history |

**Sign transaction**
```json
POST /api/v1/transaction/sign
Authorization: Bearer <token>
{
  "from": "0xabc123...",
  "to": "0xdef456...",
  "value": "1000000000000000000",
  "nonce": 0,
  "data": "0x",
  "gasLimit": 21000
}
```
```json
{
  "success": true,
  "data": {
    "signedTransaction": "0xf86c...",
    "transactionHash": "0x9f3a...",
    "from": "0xabc123...",
    "to": "0xdef456...",
    "nonce": 0
  }
}
```

The private key is derived, used for signing, and discarded — all within a single request. The application receiving the signed payload is responsible for broadcasting it, which keeps CryptoVault decoupled from any specific network.

---

## Master Seed Formats

The `MASTER_SEED` environment variable supports three formats:

| Format | Example | Use case |
|--------|---------|----------|
| `ENC:<base64>` | `ENC:abc123...` | Production — seed encrypted with AES/CBC + PBKDF2 |
| `BASE64:<base64>` | `BASE64:abc123...` | Development only — plaintext seed in Base64 |
| `<plaintext>` | `my-seed` | Development only — emits a CRITICAL log warning |

To encrypt a seed for production, use the `MasterSeedLoader.decryptMasterSeed` utility method or generate the ciphertext offline using PBKDF2-HMAC-SHA256 (100,000 iterations) + AES/CBC.

---

## Running Tests

```bash
# All tests
./gradlew test

# Specific suite
./gradlew test --tests "com.jminango.cryptovault.service.CryptoServiceTest"
./gradlew test --tests "com.jminango.cryptovault.service.EncryptionServiceTest"
```

Tests use an in-memory H2 database and do not require a running PostgreSQL instance or a live blockchain node.

---

## Security Model

| Threat | Mitigation |
|--------|-----------|
| Database breach | Only encrypted derivation indexes are stored. Without the master seed and encryption key they are useless. |
| Substitution attack | `userId` is bound as GCM AAD; copying one user's ciphertext into another record causes decryption to fail. |
| Ciphertext tampering | AES-256-GCM produces a 128-bit authentication tag; any modification is detected before decryption proceeds. |
| Child key compromise | Hardened derivation at levels `44'/60'/0'` prevents an attacker from recovering the parent key or master seed. |
| Key exposure via logs | `MasterSeed.toString()` returns only the byte length; cryptographic material is filtered from all structured logs. |
| Master seed compromise | Recovery is feasible: generate a new seed, re-derive all wallets, and migrate on-chain assets before deactivating the old seed. |

---

## Project Structure

```
src/
├── main/kotlin/com/jminango/cryptovault/
│   ├── config/          # MasterSeedLoader, SecurityConfig, RateLimitConfig
│   ├── controller/      # AuthController, WalletController, TransactionController
│   ├── dto/             # Request/response DTOs
│   ├── exception/       # Global exception handler, domain exceptions
│   ├── model/           # Wallet, Transaction, User JPA entities
│   ├── repository/      # Spring Data JPA repositories
│   └── service/
│       ├── CryptoService.kt       # BIP-44 HD key derivation (core)
│       ├── EncryptionService.kt   # AES-256-GCM encrypt/decrypt
│       ├── MasterSeedLoader.kt    # Startup seed loading
│       ├── WalletService.kt       # Wallet lifecycle
│       ├── TransactionService.kt  # Signing and broadcasting
│       └── AuthService.kt         # JWT authentication
└── test/
    └── kotlin/com/jminango/cryptovault/
        ├── service/CryptoServiceTest.kt
        ├── service/EncryptionServiceTest.kt
        └── config/MasterSeedLoaderTest.kt
```

---

## License

This project is released for academic and research purposes. See [LICENSE](LICENSE) for details.
