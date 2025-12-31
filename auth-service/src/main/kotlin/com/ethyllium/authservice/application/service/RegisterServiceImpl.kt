package com.ethyllium.authservice.application.service

import com.ethyllium.authservice.domain.exception.UserAlreadyExistsException
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.model.User
import com.ethyllium.authservice.domain.port.driven.*
import com.ethyllium.authservice.domain.port.driver.RegisterService
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.JwtTokenResponse
import com.ethyllium.authservice.infrastructure.outbound.redis.BloomFilterDLQEvent
import com.ethyllium.authservice.infrastructure.outbound.redis.BloomFilterDLQPublisher
import com.ethyllium.authservice.infrastructure.outbound.smtp.SmtpNotificationAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*

@Service
class RegisterServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
    private val bloomFilterPort: BloomFilterPort,
    private val dlqPublisher: BloomFilterDLQPublisher,
    @Value("\${TOKEN_EXPIRY_SECONDS:3600}") private val tokenExpirySeconds: Long,
    private val totpSecretGenerator: TotpSecretGenerator,
    private val qrCodeGenerator: QrCodeGenerator,
    private val notificationPort: NotificationPort,
) : RegisterService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun register(
        password: String,
        email: String,
        mfaOptions: MFAOptions
    ): Mono<RegisterResult> {
        return bloomFilterPort.contains(email)
            .flatMap { inBloomFilter ->
                if (inBloomFilter) {
                    userRepository.findBy(email).hasElements()
                } else {
                    Mono.just(false)
                }
            }
            .onErrorResume { error ->
                logger.warn("Bloom filter check failed, proceeding with registration", error)
                Mono.just(false)
            }
            .flatMap { exists ->
                if (exists) {
                    Mono.error(UserAlreadyExistsException())
                } else {
                    saveUserAndGenerateToken(email, password, mfaOptions)
                }
            }
    }

    private fun saveUserAndGenerateToken(
        email: String,
        password: String,
        mfaOptions: MFAOptions
    ): Mono<RegisterResult> {
        val userId = UUID.randomUUID()
        val newUser = User(
            userId = userId,
            email = email,
            mfaOptions = mfaOptions
        )

        return userRepository.save(
            newUser,
            passwordEncoder.encode(password)!!
        ) // passwordEncoder.encode returns null only if input is null
            .map { savedUserId ->
                val accessToken = jwtTokenService.generateAccessToken(savedUserId.toString())
                when (mfaOptions) {
                    MFAOptions.NONE -> RegisterResult.Token(
                        JwtTokenResponse(
                            accessToken = accessToken,
                            expiresIn = tokenExpirySeconds
                        )
                    )

                    MFAOptions.AUTHENTICATOR_APP -> {
                        val (secret, uri) = totpSecretGenerator.generateTotpSecret(email)
                        userRepository.addSecret(secret, savedUserId).subscribeOn(Schedulers.boundedElastic())
                            .subscribe()
                        val qrCodeBytes = qrCodeGenerator.generateQrCode(uri, 200, 200)
                        RegisterResult.Authenticator(qrCodeBytes)
                    }

                    MFAOptions.EMAIL -> {
                        notificationPort.sendEmail(email, subject = "Verify your email", body = "Please verify your email by clicking the link.")
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe()
                        RegisterResult.EmailVerification
                    }
                }
            }
            .doOnSuccess {
                updateBloomFilterAsync(email)
            }
            .onErrorMap { error ->
                when (error) {
                    is DuplicateKeyException,
                    is DataIntegrityViolationException -> UserAlreadyExistsException()

                    else -> error
                }
            }
    }

    private fun updateBloomFilterAsync(email: String) {
        bloomFilterPort.addToFilter(email)
            .onErrorResume { error ->
                logger.error("Failed to add email to bloom filter: $email", error)

                // Send to DLQ
                dlqPublisher.publishToDLQ(
                    BloomFilterDLQEvent(
                        email = email,
                        errorMessage = error.message
                    )
                ).then(Mono.empty())
            }
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()
    }
}

sealed interface RegisterResult {
    data class Token(val token: JwtTokenResponse) : RegisterResult
    data class Authenticator(val byteArray: ByteArray) : RegisterResult
    data object EmailVerification : RegisterResult
}