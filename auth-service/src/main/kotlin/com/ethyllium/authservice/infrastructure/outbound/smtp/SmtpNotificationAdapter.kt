package com.ethyllium.authservice.infrastructure.outbound.smtp

import com.ethyllium.authservice.domain.port.driven.NotificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Component
class SmtpNotificationAdapter(
    private val javaMailSender: JavaMailSender,
    @Value("\${notification.email.from:vora@gmail.com}") private val fromAddress: String
) : NotificationPort {

    override fun sendOtpEmail(email: String, otp: String): Mono<Void> {
        return Mono.fromCallable {
            val message = SimpleMailMessage()
            message.setTo(email)
            message.subject = "Your Verification Code"
            message.text = "Your secure login code is: $otp\n\nThis code expires in 5 minutes."
            message.from = fromAddress

            javaMailSender.send(message)
            null
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun sendEmail(email: String, subject: String, body: String): Mono<Void> {
        return Mono.fromCallable {
            val message = SimpleMailMessage()
            message.setTo(email)
            message.subject = subject
            message.text = body
            message.from = fromAddress
            javaMailSender.send(message)
            null
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }
}