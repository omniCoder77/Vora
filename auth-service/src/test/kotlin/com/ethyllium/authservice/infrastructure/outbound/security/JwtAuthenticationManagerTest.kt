package com.ethyllium.authservice.infrastructure.outbound.security

import com.ethyllium.authservice.domain.exception.EmptyJwtSubjectException
import com.ethyllium.authservice.domain.model.User
import com.ethyllium.authservice.domain.port.driven.JwtTokenService
import com.ethyllium.authservice.domain.port.driven.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

class JwtAuthenticationManagerTest {

    private val jwtTokenService = mockk<JwtTokenService>()
    private val userRepository = mockk<UserRepository>()
    private val authManager = JwtAuthenticationManager(jwtTokenService, userRepository)

    @Test
    fun `authenticate success - valid token and user exists`() {
        val userId = UUID.randomUUID()
        val token = "valid-token"
        val user = User(userId, "test@test.com")
        val auth = UsernamePasswordAuthenticationToken(token, token)

        every { jwtTokenService.getSubject(token) } returns userId.toString()
        every { userRepository.findBy(userId = userId) } returns Flux.just(Pair(user, "hash"))

        StepVerifier.create(authManager.authenticate(auth))
            .assertNext { result ->
                assert(result.principal is Pair<*, *>)
                assert((result.principal as Pair<User, String>).first.userId == userId)
            }
            .verifyComplete()
    }

    @Test
    fun `authenticate failure - token has no subject`() {
        val token = "invalid-token"
        val auth = UsernamePasswordAuthenticationToken(token, token)

        every { jwtTokenService.getSubject(token) } returns null

        StepVerifier.create(authManager.authenticate(auth))
            .expectError(EmptyJwtSubjectException::class.java)
            .verify()
    }
}