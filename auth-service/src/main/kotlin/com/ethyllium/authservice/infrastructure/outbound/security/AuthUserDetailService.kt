package com.ethyllium.authservice.infrastructure.outbound.security

import com.ethyllium.authservice.domain.port.driven.UserRepository
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AuthUserDetailService(private val userRepository: UserRepository) : ReactiveUserDetailsService {
    override fun findByUsername(username: String): Mono<UserDetails> {
        return userRepository.findBy(email = username).next().map { User(it.first.email, it.second, emptyList()) }
    }
}