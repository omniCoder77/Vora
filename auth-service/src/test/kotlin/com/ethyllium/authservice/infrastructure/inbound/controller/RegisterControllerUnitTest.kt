package com.ethyllium.authservice.infrastructure.inbound.controller

import com.ethyllium.authservice.application.service.RegisterResult
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.port.driver.RegisterService
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.ApiResponse
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.JwtTokenResponse
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.RegisterRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class RegisterControllerUnitTest {

    private val registerService: RegisterService = mockk()
    private val registerController = RegisterController(registerService)

    @Test
    fun `register should return Token response when MFA is NONE`() {
        // Arrange
        val email = "test@example.com"
        val password = "password123"
        val request = RegisterRequest(password, email, MFAOptions.NONE)

        val tokenResponse = JwtTokenResponse(
            accessToken = "access-token",
            expiresIn = 3600
        )

        every {
            registerService.register(password, email, MFAOptions.NONE)
        } returns Mono.just(RegisterResult.Token(tokenResponse))

        // Act & Assert
        StepVerifier.create(registerController.register(request))
            .assertNext { apiResponse ->
                assertTrue(apiResponse is ApiResponse.Success<*>)
                val successResponse = apiResponse as ApiResponse.Success<*>
                val responseEntity = successResponse.data as ResponseEntity<*>

                assertEquals(200, responseEntity.statusCode.value())
                assertEquals(tokenResponse, responseEntity.body)
            }
            .verifyComplete()

        verify(exactly = 1) { registerService.register(password, email, MFAOptions.NONE) }
    }

    @Test
    fun `register should return QR Code byte array when MFA is AUTHENTICATOR_APP`() {
        // Arrange
        val email = "mfa@example.com"
        val password = "securePass"
        val request = RegisterRequest(password, email, MFAOptions.AUTHENTICATOR_APP)

        val qrCodeBytes = byteArrayOf(1, 2, 3, 4)

        every {
            registerService.register(password, email, MFAOptions.AUTHENTICATOR_APP)
        } returns Mono.just(RegisterResult.Authenticator(qrCodeBytes))

        // Act & Assert
        StepVerifier.create(registerController.register(request))
            .assertNext { apiResponse ->
                assertTrue(apiResponse is ApiResponse.Success<*>)
                val successResponse = apiResponse as ApiResponse.Success<*>
                val responseEntity = successResponse.data as ResponseEntity<*>

                assertEquals(200, responseEntity.statusCode.value())
                assertEquals(MediaType.IMAGE_PNG, responseEntity.headers.contentType)
                assertArrayEquals(qrCodeBytes, responseEntity.body as ByteArray)
            }
            .verifyComplete()

        verify(exactly = 1) { registerService.register(password, email, MFAOptions.AUTHENTICATOR_APP) }
    }

    @Test
    fun `register should return text message when MFA is EMAIL`() {
        // Arrange
        val email = "email@example.com"
        val password = "password"
        val request = RegisterRequest(password, email, MFAOptions.EMAIL)

        every {
            registerService.register(password, email, MFAOptions.EMAIL)
        } returns Mono.just(RegisterResult.EmailVerification)

        // Act & Assert
        StepVerifier.create(registerController.register(request))
            .assertNext { apiResponse ->
                assertTrue(apiResponse is ApiResponse.Success<*>)
                val successResponse = apiResponse as ApiResponse.Success<*>
                val responseEntity = successResponse.data as ResponseEntity<*>

                assertEquals(200, responseEntity.statusCode.value())
                assertEquals("Email verification sent", responseEntity.body)
            }
            .verifyComplete()

        verify(exactly = 1) { registerService.register(password, email, MFAOptions.EMAIL) }
    }

    @Test
    fun `register should propagate exception when service fails`() {
        // Arrange
        val email = "error@example.com"
        val password = "pass"
        val request = RegisterRequest(password, email, MFAOptions.NONE)
        val expectedError = RuntimeException("Service failure")

        every {
            registerService.register(any(), any(), any())
        } returns Mono.error(expectedError)

        // Act & Assert
        StepVerifier.create(registerController.register(request))
            .expectErrorMatches { it.message == "Service failure" }
            .verify()

        verify(exactly = 1) { registerService.register(password, email, MFAOptions.NONE) }
    }
}
