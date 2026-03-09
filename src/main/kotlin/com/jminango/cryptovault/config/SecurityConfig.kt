package com.jminango.cryptovault.config

import com.jminango.cryptovault.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthenticationFilter: JwtAuthenticationFilter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }  // stateless REST API — CSRF not applicable
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/health",
                        "/api/v1/info",
                        "/api/v1/blockchain/status",
                        "/api/v1/contract/info",
                        "/api/v1/contract/balance/**",
                        "/api/v1/auth/**"
                    ).permitAll()
                    .requestMatchers(
                        "/api/v1/wallet/**",
                        "/api/v1/transaction/**",
                        "/api/v1/contract/mint",
                        "/api/v1/contract/transfer"
                    ).authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
