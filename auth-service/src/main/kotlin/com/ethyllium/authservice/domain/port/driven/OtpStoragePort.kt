package com.ethyllium.authservice.domain.port.driven

import reactor.core.publisher.Mono

interface OtpStoragePort {
    fun saveOtp(email: String, otp: String): Mono<Boolean>
    fun getOtp(email: String): Mono<String>
    fun deleteOtp(email: String): Mono<Long>
    fun incrementAttempts(email: String): Mono<Long>
}