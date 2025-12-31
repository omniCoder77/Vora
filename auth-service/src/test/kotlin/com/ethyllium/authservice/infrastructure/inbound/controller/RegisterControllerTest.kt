package com.ethyllium.authservice.infrastructure.inbound.controller

import com.ethyllium.authservice.application.service.RegisterResult
import com.ethyllium.authservice.domain.exception.UserAlreadyExistsException
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.port.driven.JwtTokenService
import com.ethyllium.authservice.domain.port.driver.RegisterService
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.JwtTokenResponse
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.RegisterRequest
import com.ethyllium.authservice.infrastructure.outbound.security.AuthUserDetailService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@WebFluxTest(controllers = [RegisterController::class])
@AutoConfigureWebTestClient
class RegisterControllerTest {

    @MockitoBean
    private lateinit var registerService: RegisterService

    @MockitoBean
    private lateinit var jwtTokenService: JwtTokenService

    @MockitoBean
    private lateinit var userDetailService: AuthUserDetailService

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `register non-mfa - returns token`() {
        val mockResponse = RegisterResult.Token(
            JwtTokenResponse(accessToken = "access-token", expiresIn = 3600)
        )
        doReturn(Mono.just(mockResponse))
            .whenever(registerService)
            .register(any(), any(), eq(MFAOptions.NONE))

        val request = RegisterRequest("test@gmail.com", "pass", MFAOptions.NONE)

        webTestClient.post().uri("/api/v1/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.body.accessToken").isEqualTo("access-token")
    }

    @Test
    fun `register authenticator app - returns QR code`() {
        val qrBytes = byteArrayOf(1, 2, 3, 4)
        val mockResponse = RegisterResult.Authenticator(qrBytes)

        doReturn(Mono.just(mockResponse))
            .whenever(registerService)
            .register(any(), any(), eq(MFAOptions.AUTHENTICATOR_APP))

        val request = RegisterRequest("mfa@gmail.com", "pass", MFAOptions.AUTHENTICATOR_APP)

        webTestClient.post().uri("/api/v1/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.data.statusCode").isEqualTo("200 OK")
    }

    @Test
    fun `register email mfa - returns verification message`() {
        val mockResponse = RegisterResult.EmailVerification

        doReturn(Mono.just(mockResponse))
            .whenever(registerService)
            .register(any(), any(), eq(MFAOptions.EMAIL))

        val request = RegisterRequest("email@gmail.com", "pass", MFAOptions.EMAIL)

        webTestClient.post().uri("/api/v1/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.body").isEqualTo("Email verification sent")
    }

    @Test
    fun `register user already exists - returns 409 conflict`() {
        doReturn(Mono.error<RegisterResult>(UserAlreadyExistsException()))
            .whenever(registerService)
            .register(any(), any(), any())

        val request = RegisterRequest("existing@gmail.com", "pass", MFAOptions.NONE)

        webTestClient.post().uri("/api/v1/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `register generic failure - returns 500 server error`() {
        doReturn(Mono.error<RegisterResult>(RuntimeException("Database down")))
            .whenever(registerService)
            .register(any(), any(), any())

        val request = RegisterRequest("fail@gmail.com", "pass", MFAOptions.NONE)

        webTestClient.post().uri("/api/v1/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError
    }
}