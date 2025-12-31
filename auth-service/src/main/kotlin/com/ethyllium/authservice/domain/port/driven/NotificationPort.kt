package com.ethyllium.authservice.domain.port.driven

import reactor.core.publisher.Mono

interface NotificationPort {
    fun sendOtpEmail(email: String, otp: String): Mono<Void>
    fun sendEmail(email: String, subject: String, body: String): Mono<Void>
}