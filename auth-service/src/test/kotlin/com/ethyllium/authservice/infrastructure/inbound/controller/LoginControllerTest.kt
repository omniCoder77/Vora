package com.ethyllium.authservice.infrastructure.inbound.controller

import com.ethyllium.authservice.application.service.LoginResult
import com.ethyllium.authservice.domain.exception.InvalidCredentialsException
import com.ethyllium.authservice.domain.port.driven.JwtTokenService
import com.ethyllium.authservice.domain.port.driver.LoginService
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.LoginRequest
import com.ethyllium.authservice.infrastructure.outbound.security.AuthUserDetailService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@WebFluxTest(controllers = [LoginController::class])
@AutoConfigureWebTestClient
class LoginControllerTest {

    @MockitoBean
    private lateinit var loginService: LoginService

    @MockitoBean
    private lateinit var jwtTokenService: JwtTokenService

    @MockitoBean
    private lateinit var userDetailService: AuthUserDetailService

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `login success - returns 200 and token`() {
        val mockResult = LoginResult.Success("mock-access-token")

        doReturn(Mono.just(mockResult))
            .whenever(loginService)
            .login(any(), any())

        val request = mapOf(
            "email" to "test@example.com",
            "password" to "password"
        )

        webTestClient.post().uri("/api/v1/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").isEqualTo("mock-access-token")
            .jsonPath("$.tokenType").isEqualTo("Bearer")
    }

    @Test
    fun `login requires MFA - returns 202 and temp token`() {
        val mockResult = LoginResult.RequiresMfa("temp-token-123", "APP")

        doReturn(Mono.just(mockResult))
            .whenever(loginService)
            .login(any(), any())

        val request = mapOf(
            "email" to "test@example.com",
            "password" to "password"
        )

        webTestClient.post().uri("/api/v1/login")
            .bodyValue(request)
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.mfaType").isEqualTo("APP")
            .jsonPath("$.tempToken").isEqualTo("temp-token-123")
    }

    @Test
    fun `login failed - returns 401`() {
        doReturn(Mono.error<LoginResult>(InvalidCredentialsException()))
            .whenever(loginService)
            .login(any(), any())

        val request = mapOf(
            "email" to "test@example.com",
            "password" to "password"
        )

        webTestClient.post().uri("/api/v1/login")
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `verify MFA success - returns 200 and final token`() {
        val mockResult = LoginResult.Success("final-access-token")

        doReturn(Mono.just(mockResult))
            .whenever(loginService)
            .verifyMfaAndLogin(any(), any())

        val mfaRequest = mapOf(
            "tempToken" to "temp-token-123",
            "code" to "123456"
        )

        webTestClient.post().uri("/api/v1/login/mfa")
            .bodyValue(mfaRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").isEqualTo("final-access-token")
    }

    @Test
    fun `verify MFA failure - returns 401`() {
        val mockResult = LoginResult.RequiresMfa("retry", "APP")

        doReturn(Mono.just(mockResult))
            .whenever(loginService)
            .verifyMfaAndLogin(any(), any())

        val mfaRequest = mapOf(
            "tempToken" to "temp-token-123",
            "code" to "000000"
        )

        webTestClient.post().uri("/api/v1/login/mfa")
            .bodyValue(mfaRequest)
            .exchange()
            .expectStatus().isUnauthorized
    }
}