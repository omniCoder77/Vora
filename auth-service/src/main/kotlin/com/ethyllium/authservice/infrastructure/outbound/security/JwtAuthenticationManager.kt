package com.ethyllium.authservice.infrastructure.outbound.security

import com.ethyllium.authservice.domain.exception.EmptyJwtSubjectException
import com.ethyllium.authservice.domain.port.driven.JwtTokenService
import com.ethyllium.authservice.domain.port.driven.UserRepository
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*

@Component
class JwtAuthenticationManager(
    private val jwtTokenService: JwtTokenService,
    private val userRepository: UserRepository
) : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        val token = authentication.credentials.toString()

        return Mono.justOrEmpty(jwtTokenService.getSubject(token))
            .switchIfEmpty(Mono.error<String>(EmptyJwtSubjectException()))
            .flatMap { userId ->
                val userUuid = UUID.fromString(userId)
                userRepository.findBy(userId = userUuid).next()
            }
            .switchIfEmpty(Mono.error(EmptyJwtSubjectException()))
            .map { user ->
                UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    emptyList()
                )
            }
    }
}
