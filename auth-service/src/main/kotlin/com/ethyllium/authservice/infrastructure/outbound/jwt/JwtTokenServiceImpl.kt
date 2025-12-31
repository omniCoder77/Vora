package com.ethyllium.authservice.infrastructure.outbound.jwt

import com.ethyllium.authservice.domain.port.driven.JwtTokenService
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*
import kotlin.jvm.java

@Service
class JwtTokenServiceImpl(
    @Value("\${jwt.token.access.token.expiry}") private val accessTokenExpiration: Long,
    @Value("\${jwt.token.refresh.token.expiry}") private val refreshTokenExpiration: Long,
    private val jwtKeyManager: JwtKeyManager
) : JwtTokenService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val jwtParser by lazy {
        Jwts.parser()
            .verifyWith(jwtKeyManager.publicKey)
            .build()
    }

    override fun generateAccessToken(subject: String, additionalClaims: Map<String, Any>): String {
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpiration)

        return Jwts.builder()
            .claims(additionalClaims)
            .subject(subject)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(jwtKeyManager.privateKey, Jwts.SIG.RS256)
            .compact()
    }

    override fun generateRefreshToken(subject: String, additionalClaims: Map<String, Any>): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshTokenExpiration)

        val claims = additionalClaims.toMutableMap()
        claims.putIfAbsent("type", "refresh")

        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(jwtKeyManager.privateKey, Jwts.SIG.RS256)
            .compact()
    }

    override fun validateToken(token: String): String? {
        return try {
            jwtParser.parseSignedClaims(token).payload.subject
        } catch (e: JwtException) {
            logger.error("Invalid JWT token", e)
            null
        } catch (e: IllegalArgumentException) {
            logger.error("JWT token compact of handler are invalid", e)
            null
        }
    }

    override fun getClaims(token: String): Claims? {
        return try {
            jwtParser.parseSignedClaims(token).payload
        } catch (e: Exception) {
            logger.error("Invalid JWT token", e)
            null
        }
    }

    override fun getSubject(token: String): String? {
        return getClaims(token)?.subject
    }

    override fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}