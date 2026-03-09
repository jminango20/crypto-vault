package com.jminango.cryptovault.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Entidade de usuário do sistema
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_username", columnList = "username"),
        Index(name = "idx_email", columnList = "email")
    ]
)
data class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 50)
    val username: String,

    @Column(nullable = false, length = 255)
    val password: String,  // Hash BCrypt

    @Column(unique = true, length = 100)
    val email: String? = null,

    @Column(nullable = false, length = 20)
    val role: String = "USER",  // USER, ADMIN

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)