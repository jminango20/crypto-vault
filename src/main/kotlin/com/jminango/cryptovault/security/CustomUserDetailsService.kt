package com.jminango.cryptovault.security

import com.jminango.cryptovault.repository.UserRepository
import mu.KotlinLogging
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Implementação customizada do UserDetailsService
 * Carrega informações do usuário do banco de dados
 */
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    /**
     * Carrega usuário por username para autenticação Spring Security
     *
     * @param username Username do usuário
     * @return UserDetails com informações de autenticação
     * @throws UsernameNotFoundException se usuário não existe
     */
    override fun loadUserByUsername(username: String): UserDetails {
        logger.debug { "Loading user details for: $username" }

        val user = userRepository.findByUsername(username)
            .orElseThrow {
                logger.warn { "User not found: $username" }
                UsernameNotFoundException("User not found: $username")
            }

        logger.debug {
            "User loaded successfully - username: ${user.username}, " +
                    "role: ${user.role}, enabled: ${user.enabled}"
        }

        // Converter para UserDetails do Spring Security
        return User.builder()
            .username(user.username)
            .password(user.password)  // BCrypt hash
            .authorities(SimpleGrantedAuthority("ROLE_${user.role}"))
            .accountExpired(!user.enabled)
            .accountLocked(!user.enabled)
            .credentialsExpired(false)
            .disabled(!user.enabled)
            .build()
    }
}