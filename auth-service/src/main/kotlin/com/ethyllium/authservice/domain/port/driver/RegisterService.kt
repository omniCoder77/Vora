package com.ethyllium.authservice.domain.port.driver

import com.ethyllium.authservice.application.service.RegisterResult
import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.JwtTokenResponse
import reactor.core.publisher.Mono

interface RegisterService {
    fun register(password: String, email: String, mfaOptions: MFAOptions): Mono<RegisterResult>
}