package com.ethyllium.authservice.application.config

import com.ethyllium.authservice.infrastructure.outbound.security.JwtAuthenticationFilter
import com.ethyllium.authservice.infrastructure.outbound.security.JwtAuthenticationManager
import com.ethyllium.authservice.infrastructure.outbound.security.JwtSecurityContextRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository

@Configuration
class SecurityConfig(
    @Value("\${encoder.strength:10}") private val strength: Int
) {

    companion object {
        const val MAX_BCRYPT_STRENGTH = 32
        const val MIN_BCRYPT_STRENGTH = 4
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        if (strength < MIN_BCRYPT_STRENGTH) {
            throw IllegalArgumentException("BCrypt strength must be at least $MIN_BCRYPT_STRENGTH")
        }
        if (strength > MAX_BCRYPT_STRENGTH) {
            throw IllegalArgumentException("BCrypt strength must be at most $MAX_BCRYPT_STRENGTH")
        }
        return BCryptPasswordEncoder(strength)
    }

    @Bean
    fun securityFilterChain(
        http: ServerHttpSecurity,
        jwtSecurityContextRepository: JwtSecurityContextRepository,
        authenticationManager: JwtAuthenticationManager, jwtAuthenticationFilter: JwtAuthenticationFilter
    ): SecurityWebFilterChain {
        val disabledDefaults = http
            .cors { it.disable() }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }

        val authConfigured = disabledDefaults
            .authenticationManager(authenticationManager)
            .securityContextRepository(jwtSecurityContextRepository)
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

        return authConfigured
            .authorizeExchange { authorize ->
                authorize
                    .pathMatchers("/api/v1/register", "/api/v1/login")
                    .permitAll()
                    .anyExchange()
                    .authenticated()
            }
            .build()
    }

}