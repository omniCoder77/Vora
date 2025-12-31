package com.ethyllium.authservice.application.service

import com.ethyllium.authservice.domain.exception.InvalidCredentialsException
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.port.driven.*
import com.ethyllium.authservice.domain.port.driver.LoginService
import com.ethyllium.authservice.domain.port.driver.MfaSetupDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.SecureRandom
import java.util.*

@Service
class LoginServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
    private val mfaTokenProvider: MfaTokenProvider,
    private val notificationPort: NotificationPort,
    private val otpStoragePort: OtpStoragePort
) : LoginService {

    private val secureRandom = SecureRandom()

    override fun login(email: String, password: CharArray): Mono<LoginResult> {
        return userRepository.findBy(email = email)
            .next()
            .flatMap { (user, storedHash) ->
                if (passwordEncoder.matches(String(password), storedHash)) {
                    when (user.mfaOptions) {
                        MFAOptions.NONE -> {
                            val token = jwtTokenService.generateAccessToken(user.userId.toString())
                            Mono.just(LoginResult.Success(token))
                        }
                        MFAOptions.AUTHENTICATOR_APP -> {
                            val tempToken = generateTempToken(user.userId.toString())
                            Mono.just(LoginResult.RequiresMfa(tempToken, "APP"))
                        }
                        MFAOptions.EMAIL -> {
                            val otp = String.format("%06d", secureRandom.nextInt(1000000))

                            otpStoragePort.saveOtp(user.email, otp)
                                .then(notificationPort.sendOtpEmail(user.email, otp))
                                .then(Mono.defer {
                                    val tempToken = generateTempToken(user.userId.toString())
                                    Mono.just(LoginResult.RequiresMfa(tempToken, "EMAIL"))
                                })
                        }
                    }
                } else {
                    Mono.error(InvalidCredentialsException())
                }
            }
            .switchIfEmpty(Mono.error(InvalidCredentialsException()))
    }

    override  fun verifyMfaAndLogin(tempToken: String, code: Int): Mono<LoginResult> {
        return Mono.fromCallable { jwtTokenService.getClaims(tempToken) }
            .filter { claims -> claims?.get("scope") == "PRE_AUTH_MFA" }
            .switchIfEmpty(Mono.error(InvalidCredentialsException()))
            .flatMap { claims ->
                val userId =  claims?.subject ?: return@flatMap Mono.error(InvalidCredentialsException())
                userRepository.findBy(userId = UUID.fromString(userId)).next()
                    .flatMap { (user, _) ->
                        when (user.mfaOptions) {
                            MFAOptions.AUTHENTICATOR_APP -> {
                                if (user.mfaSecret != null && mfaTokenProvider.validateCode(user.mfaSecret, code)) {
                                    return@flatMap Mono.just(LoginResult.Success(jwtTokenService.generateAccessToken(userId)))
                                }
                                Mono.error(InvalidCredentialsException())

                            }
                            MFAOptions.EMAIL -> {
                                otpStoragePort.getOtp(user.email)
                                    .flatMap { storedOtp ->
                                        if (storedOtp == code.toString()) {
                                            otpStoragePort.deleteOtp(user.email)
                                                .then(Mono.just(LoginResult.Success(jwtTokenService.generateAccessToken(userId))))
                                        } else {
                                            otpStoragePort.incrementAttempts(user.email).flatMap { attempts ->
                                                if (attempts >= 5) {
                                                    otpStoragePort.deleteOtp(user.email).subscribeOn(Schedulers.boundedElastic()).subscribe()
                                                }
                                                Mono.error<LoginResult>(InvalidCredentialsException())
                                            }
                                        }
                                    }
                                    .switchIfEmpty(Mono.error(InvalidCredentialsException()))
                            }
                            else -> {
                                Mono.error(InvalidCredentialsException())
                            }
                        }
                    }
            }
    }

    private fun generateTempToken(userId: String): String {
        return jwtTokenService.generateAccessToken(userId, mapOf("scope" to "PRE_AUTH_MFA"))
    }

    override fun initiateMfaSetup(userId: String): Mono<MfaSetupDetails> {
        return userRepository.findBy(userId = UUID.fromString(userId)).next()
            .flatMap { (user, hashedPassword) ->
                val secret = mfaTokenProvider.generateSecret()
                val updatedUser = user.copy(mfaSecret = secret)

                userRepository.save(updatedUser, hashedPassword)
                    .map {
                        MfaSetupDetails(
                            secret = secret,
                            qrCodeUri = mfaTokenProvider.getQrCodeUri(secret, user.email)
                        )
                    }
            }
    }

    override fun confirmMfaSetup(userId: String, code: Int): Mono<Boolean> {
        return userRepository.findBy(userId = UUID.fromString(userId)).next()
            .flatMap { (user, hashedPassword) ->
                if (user.mfaSecret != null && mfaTokenProvider.validateCode(user.mfaSecret, code)) {
                    val activatedUser = user.copy(mfaOptions = MFAOptions.AUTHENTICATOR_APP)
                    userRepository.save(activatedUser, hashedPassword).map { true }
                } else {
                    Mono.just(false)
                }
            }
    }
}

sealed class LoginResult {
    data class Success(val accessToken: String) : LoginResult()
    data class RequiresMfa(val tempToken: String, val type: String) : LoginResult()
}
