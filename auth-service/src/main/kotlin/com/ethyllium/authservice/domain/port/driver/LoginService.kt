package com.ethyllium.authservice.domain.port.driver

import com.ethyllium.authservice.application.service.LoginResult
import reactor.core.publisher.Mono

interface LoginService {
    fun login(email: String, password: CharArray): Mono<LoginResult>
    fun verifyMfaAndLogin(tempToken: String, code: Int): Mono<LoginResult>

    fun initiateMfaSetup(userId: String): Mono<MfaSetupDetails>
    fun confirmMfaSetup(userId: String, code: Int): Mono<Boolean>
}

data class MfaSetupDetails(val secret: String, val qrCodeUri: String)