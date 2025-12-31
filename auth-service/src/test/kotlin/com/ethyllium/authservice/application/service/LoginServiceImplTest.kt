package com.ethyllium.authservice.application.service

import com.ethyllium.authservice.domain.exception.InvalidCredentialsException
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.model.User
import com.ethyllium.authservice.domain.port.driven.*
import io.jsonwebtoken.Claims
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

class LoginServiceImplTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenService = mockk<JwtTokenService>()
    private val mfaTokenProvider = mockk<MfaTokenProvider>()
    private val notificationPort = mockk<NotificationPort>()
    private val otpStoragePort = mockk<OtpStoragePort>()

    private lateinit var loginService: LoginServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testEmail = "test@example.com"
    private val testPassword = "password".toCharArray()
    private val testHash = "hashed_password"
    private val testUser = User(testUserId, testEmail, MFAOptions.NONE)

    @BeforeEach
    fun setup() {
        loginService = LoginServiceImpl(
            userRepository, passwordEncoder, jwtTokenService,
            mfaTokenProvider, notificationPort, otpStoragePort
        )
    }

    @Test
    fun `login success - no MFA`() {
        every { userRepository.findBy(email = testEmail) } returns Flux.just(Pair(testUser, testHash))
        every { passwordEncoder.matches(any(), testHash) } returns true
        every { jwtTokenService.generateAccessToken(testUserId.toString()) } returns "access-token"

        StepVerifier.create(loginService.login(testEmail, testPassword))
            .expectNext(LoginResult.Success("access-token"))
            .verifyComplete()
    }

    @Test
    fun `login failure - invalid password`() {
        every { userRepository.findBy(email = testEmail) } returns Flux.just(Pair(testUser, testHash))
        every { passwordEncoder.matches(any(), testHash) } returns false

        StepVerifier.create(loginService.login(testEmail, testPassword))
            .expectError(InvalidCredentialsException::class.java)
            .verify()
    }

    @Test
    fun `login success - requires Authenticator App MFA`() {
        val mfaUser = testUser.copy(mfaOptions = MFAOptions.AUTHENTICATOR_APP)
        every { userRepository.findBy(email = testEmail) } returns Flux.just(Pair(mfaUser, testHash))
        every { passwordEncoder.matches(any(), testHash) } returns true
        every { jwtTokenService.generateAccessToken(any(), any()) } returns "temp-token"

        StepVerifier.create(loginService.login(testEmail, testPassword))
            .expectNext(LoginResult.RequiresMfa("temp-token", "APP"))
            .verifyComplete()

        verify { jwtTokenService.generateAccessToken(testUserId.toString(), mapOf("scope" to "PRE_AUTH_MFA")) }
    }

    @Test
    fun `login success - requires Email MFA`() {
        val mfaUser = testUser.copy(mfaOptions = MFAOptions.EMAIL)
        every { userRepository.findBy(email = testEmail) } returns Flux.just(Pair(mfaUser, testHash))
        every { passwordEncoder.matches(any(), testHash) } returns true
        every { otpStoragePort.saveOtp(testEmail, any()) } returns Mono.just(true)
        every { notificationPort.sendOtpEmail(testEmail, any()) } returns Mono.empty()
        every { jwtTokenService.generateAccessToken(any(), any()) } returns "temp-token"

        StepVerifier.create(loginService.login(testEmail, testPassword))
            .expectNext(LoginResult.RequiresMfa("temp-token", "EMAIL"))
            .verifyComplete()

        verify { otpStoragePort.saveOtp(testEmail, match { it.length == 6 }) }
        verify { notificationPort.sendOtpEmail(testEmail, any()) }
    }

    @Test
    fun `verifyMfaAndLogin - App success`() {
        val tempToken = "valid-temp-token"
        val mfaUser = testUser.copy(mfaOptions = MFAOptions.AUTHENTICATOR_APP, mfaSecret = "secret")
        val claims = mockk<Claims>()

        every { jwtTokenService.getClaims(tempToken) } returns claims
        every { claims["scope"] } returns "PRE_AUTH_MFA"
        every { claims.subject } returns testUserId.toString()
        every { userRepository.findBy(userId = testUserId) } returns Flux.just(Pair(mfaUser, testHash))
        every { mfaTokenProvider.validateCode("secret", 123456) } returns true
        every { jwtTokenService.generateAccessToken(testUserId.toString()) } returns "final-token"

        StepVerifier.create(loginService.verifyMfaAndLogin(tempToken, 123456))
            .expectNext(LoginResult.Success("final-token"))
            .verifyComplete()
    }

    @Test
    fun `verifyMfaAndLogin - Email success`() {
        val tempToken = "valid-temp-token"
        val mfaUser = testUser.copy(mfaOptions = MFAOptions.EMAIL)
        val claims = mockk<Claims>()

        every { jwtTokenService.getClaims(tempToken) } returns claims
        every { claims["scope"] } returns "PRE_AUTH_MFA"
        every { claims.subject } returns testUserId.toString()
        every { userRepository.findBy(userId = testUserId) } returns Flux.just(Pair(mfaUser, testHash))
        every { otpStoragePort.getOtp(testEmail) } returns Mono.just("654321")
        every { otpStoragePort.deleteOtp(testEmail) } returns Mono.just(1L)
        every { jwtTokenService.generateAccessToken(testUserId.toString()) } returns "final-token"

        StepVerifier.create(loginService.verifyMfaAndLogin(tempToken, 654321))
            .expectNext(LoginResult.Success("final-token"))
            .verifyComplete()
    }

    @Test
    fun `verifyMfaAndLogin - Email failure and increment attempts`() {
        val tempToken = "valid-temp-token"
        val mfaUser = testUser.copy(mfaOptions = MFAOptions.EMAIL)
        val claims = mockk<Claims>()

        every { jwtTokenService.getClaims(tempToken) } returns claims
        every { claims["scope"] } returns "PRE_AUTH_MFA"
        every { claims.subject } returns testUserId.toString()
        every { userRepository.findBy(userId = testUserId) } returns Flux.just(Pair(mfaUser, testHash))
        every { otpStoragePort.getOtp(testEmail) } returns Mono.just("654321")
        every { otpStoragePort.incrementAttempts(testEmail) } returns Mono.just(1L)

        StepVerifier.create(loginService.verifyMfaAndLogin(tempToken, 111111))
            .expectError(InvalidCredentialsException::class.java)
            .verify()

        verify { otpStoragePort.incrementAttempts(testEmail) }
    }

    @Test
    fun `initiateMfaSetup - success`() {
        every { userRepository.findBy(userId = testUserId) } returns Flux.just(Pair(testUser, testHash))
        every { mfaTokenProvider.generateSecret() } returns "new-secret"
        every { mfaTokenProvider.getQrCodeUri("new-secret", testEmail) } returns "qr-uri"
        every { userRepository.save(any(), testHash) } returns Mono.just(testUserId)

        StepVerifier.create(loginService.initiateMfaSetup(testUserId.toString()))
            .expectNextMatches { it.secret == "new-secret" && it.qrCodeUri == "qr-uri" }
            .verifyComplete()

        verify { userRepository.save(match { it.mfaSecret == "new-secret" }, testHash) }
    }

    @Test
    fun `confirmMfaSetup - success`() {
        val userWithSecret = testUser.copy(mfaSecret = "secret")
        every { userRepository.findBy(userId = testUserId) } returns Flux.just(Pair(userWithSecret, testHash))
        every { mfaTokenProvider.validateCode("secret", 123456) } returns true
        every { userRepository.save(any(), testHash) } returns Mono.just(testUserId)

        StepVerifier.create(loginService.confirmMfaSetup(testUserId.toString(), 123456))
            .expectNext(true)
            .verifyComplete()

        verify { userRepository.save(match { it.mfaOptions == MFAOptions.AUTHENTICATOR_APP }, testHash) }
    }
}