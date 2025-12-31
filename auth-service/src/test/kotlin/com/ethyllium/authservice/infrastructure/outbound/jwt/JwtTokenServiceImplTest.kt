package com.ethyllium.authservice.infrastructure.outbound.jwt
import io.jsonwebtoken.Jwts
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class JwtTokenServiceImplTest {

    private lateinit var jwtService: JwtTokenServiceImpl
    private val jwtKeyManager = mockk<JwtKeyManager>()

    private val accessTokenExpiry = 3600000L
    private val refreshTokenExpiry = 86400000L
    private lateinit var testKeyPair: KeyPair

    @BeforeEach
    fun setup() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        testKeyPair = keyGen.generateKeyPair()

        every { jwtKeyManager.privateKey } returns testKeyPair.private as RSAPrivateKey
        every { jwtKeyManager.publicKey } returns testKeyPair.public as RSAPublicKey

        jwtService = JwtTokenServiceImpl(
            accessTokenExpiry,
            refreshTokenExpiry,
            jwtKeyManager
        )
    }

    @Nested
    @DisplayName("Token Generation Tests")
    inner class GenerationTests {

        @Test
        fun `should generate a valid access token with claims`() {
            val subject = "user@example.com"
            val claims = mapOf("role" to "ADMIN")

            val token = jwtService.generateAccessToken(subject, claims)

            assertNotNull(token)
            val parsedClaims = jwtService.getClaims(token)
            assertEquals(subject, parsedClaims?.subject)
            assertEquals("ADMIN", parsedClaims?.get("role"))
        }

        @Test
        fun `should include refresh type in refresh token`() {
            val token = jwtService.generateRefreshToken("user123", emptyMap())

            val claims = jwtService.getClaims(token)
            assertEquals("refresh", claims?.get("type"))
        }
    }

    @Nested
    @DisplayName("Validation & Security Tests")
    inner class ValidationTests {

        @Test
        fun `should return subject for valid token`() {
            val token = jwtService.generateAccessToken("test-user", emptyMap())
            val subject = jwtService.validateToken(token)
            assertEquals("test-user", subject)
        }

        @Test
        fun `should return null for expired token`() {
            val expiredService = JwtTokenServiceImpl(-1000, -1000, jwtKeyManager)
            val token = expiredService.generateAccessToken("expired-user", emptyMap())

            val result = jwtService.validateToken(token)
            assertNull(result, "Expired token should return null")
        }

        @Test
        fun `should return null for token signed with wrong key`() {
            val wrongKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

            val maliciousToken = Jwts.builder()
                .subject("hacker")
                .signWith(wrongKeyPair.private, Jwts.SIG.RS256)
                .compact()

            val result = jwtService.validateToken(maliciousToken)
            assertNull(result, "Token with invalid signature should return null")
        }

        @Test
        fun `should return null for malformed token string`() {
            val result = jwtService.validateToken("not.a.jwt.token")
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Utility Tests")
    inner class UtilityTests {

        @Test
        fun `generateSecureToken should return unique high-entropy strings`() {
            val token1 = jwtService.generateSecureToken()
            val token2 = jwtService.generateSecureToken()

            assertNotEquals(token1, token2)
            assertTrue(token1.length >= 32)
        }
    }
}