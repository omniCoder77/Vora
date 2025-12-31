package com.ethyllium.authservice.application.service

import com.ethyllium.authservice.domain.exception.UserAlreadyExistsException
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.model.User
import com.ethyllium.authservice.domain.port.driven.*
import com.ethyllium.authservice.infrastructure.outbound.redis.BloomFilterDLQPublisher
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

class RegisterServiceImplTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenService = mockk<JwtTokenService>()
    private val bloomFilterPort = mockk<BloomFilterPort>()
    private val dlqPublisher = mockk<BloomFilterDLQPublisher>()
    private val totpSecretGenerator = mockk<TotpSecretGenerator>()
    private val qrCodeGenerator = mockk<QrCodeGenerator>()
    private val notificationPort = mockk<NotificationPort>()
    private val tokenExpiry = 3600L

    private lateinit var registerService: RegisterServiceImpl

    @BeforeEach
    fun setup() {
        registerService = RegisterServiceImpl(
            userRepository, passwordEncoder, jwtTokenService, bloomFilterPort,
            dlqPublisher, tokenExpiry, totpSecretGenerator, qrCodeGenerator, notificationPort
        )
    }

    @Test
    fun `register success with MFA NONE`() {
        val email = "new@test.com"
        val password = "rawPassword"
        val userId = UUID.randomUUID()

        every { bloomFilterPort.contains(email) } returns Mono.just(false)
        every { passwordEncoder.encode(password) } returns "hashed"
        every { userRepository.save(any(), "hashed") } returns Mono.just(userId)
        every { jwtTokenService.generateAccessToken(userId.toString()) } returns "access-token"

        every { bloomFilterPort.addToFilter(email) } returns Mono.just(true)

        val result = registerService.register(password, email, MFAOptions.NONE)

        StepVerifier.create(result)
            .assertNext { res ->
                assert(res is RegisterResult.Token)
                val tokenRes = res as RegisterResult.Token
                assert(tokenRes.token.accessToken == "access-token")
            }
            .verifyComplete()

        verify(exactly = 1) { userRepository.save(match { it.email == email }, "hashed") }
        verify(exactly = 0) { userRepository.findBy(email) }
    }

    @Test
    fun `register success with AUTHENTICATOR_APP`() {
        val email = "mfa@test.com"
        val userId = UUID.randomUUID()
        val qrCode = byteArrayOf(1, 2, 3)

        every { bloomFilterPort.contains(email) } returns Mono.just(false)
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any(), any()) } returns Mono.just(userId)
        every { jwtTokenService.generateAccessToken(any()) } returns "token"

        every { totpSecretGenerator.generateTotpSecret(email) } returns Pair("secret", "otpauth://uri")
        every { userRepository.addSecret("secret", userId) } returns Mono.just(1L)
        every { qrCodeGenerator.generateQrCode("otpauth://uri", 200, 200) } returns qrCode
        every { bloomFilterPort.addToFilter(email) } returns Mono.just(true)

        val result = registerService.register("pass", email, MFAOptions.AUTHENTICATOR_APP)

        StepVerifier.create(result)
            .assertNext { res ->
                assert(res is RegisterResult.Authenticator)
                assert((res as RegisterResult.Authenticator).byteArray.contentEquals(qrCode))
            }
            .verifyComplete()

        verify { userRepository.addSecret("secret", userId) }
    }

    @Test
    fun `register success with EMAIL MFA`() {
        val email = "email@test.com"
        val userId = UUID.randomUUID()

        every { bloomFilterPort.contains(email) } returns Mono.just(false)
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any(), any()) } returns Mono.just(userId)
        every { jwtTokenService.generateAccessToken(any()) } returns "token"

        every { notificationPort.sendEmail(email, any(), any()) } returns Mono.empty()
        every { bloomFilterPort.addToFilter(email) } returns Mono.just(true)

        val result = registerService.register("pass", email, MFAOptions.EMAIL)

        StepVerifier.create(result)
            .expectNext(RegisterResult.EmailVerification)
            .verifyComplete()

        verify { notificationPort.sendEmail(email, "Verify your email", any()) }
    }

    @Test
    fun `register fails when user exists in database (Bloom Filter Positive)`() {
        val email = "exists@test.com"

        every { bloomFilterPort.contains(email) } returns Mono.just(true)
        every { userRepository.findBy(email) } returns Flux.just(Pair(mockk<User>(), "hash"))

        val result = registerService.register("pass", email, MFAOptions.NONE)

        StepVerifier.create(result)
            .expectError(UserAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `register fails on DB unique constraint violation`() {
        val email = "duplicate@test.com"

        every { bloomFilterPort.contains(email) } returns Mono.just(false)
        every { passwordEncoder.encode(any()) } returns "hash"
        every { userRepository.save(any(), any()) } returns Mono.error(DuplicateKeyException("Duplicate"))

        val result = registerService.register("pass", email, MFAOptions.NONE)

        StepVerifier.create(result)
            .expectError(UserAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `async bloom filter update failure should publish to DLQ`() {
        val email = "dlq@test.com"
        val userId = UUID.randomUUID()

        every { bloomFilterPort.contains(email) } returns Mono.just(false)
        every { passwordEncoder.encode(any()) } returns "hash"
        every { userRepository.save(any(), any()) } returns Mono.just(userId)
        every { jwtTokenService.generateAccessToken(any()) } returns "token"

        every { bloomFilterPort.addToFilter(email) } returns Mono.error(RuntimeException("Redis Down"))
        every { dlqPublisher.publishToDLQ(any()) } returns Mono.just(mockk())

        val result = registerService.register("pass", email, MFAOptions.NONE)

        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(timeout = 1000) { dlqPublisher.publishToDLQ(match { it.email == email }) }
    }

    @Test
    fun `bloom filter error should not block registration`() {
        val email = "error@test.com"

        every { bloomFilterPort.contains(email) } returns Mono.error(RuntimeException("Timeout"))

        every { passwordEncoder.encode(any()) } returns "hash"
        every { userRepository.save(any(), any()) } returns Mono.just(UUID.randomUUID())
        every { jwtTokenService.generateAccessToken(any()) } returns "token"
        every { bloomFilterPort.addToFilter(email) } returns Mono.just(true)

        val result = registerService.register("pass", email, MFAOptions.NONE)

        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()
    }
}