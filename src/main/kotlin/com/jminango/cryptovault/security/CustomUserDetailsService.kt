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


@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        logger.debug { "Loading user: $username" }

        val user = userRepository.findByUsername(username)
            .orElseThrow {
                UsernameNotFoundException("User not found: $username")
            }

        // Converter para UserDetails do Spring Security
        return User.builder()
            .username(user.username)
            .password(user.password)
            .authorities(SimpleGrantedAuthority("ROLE_${user.role}"))
            .accountExpired(!user.enabled)
            .accountLocked(!user.enabled)
            .credentialsExpired(false)
            .disabled(!user.enabled)
            .build()
    }
}