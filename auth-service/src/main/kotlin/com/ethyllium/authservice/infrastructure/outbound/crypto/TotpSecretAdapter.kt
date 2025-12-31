package com.ethyllium.authservice.infrastructure.outbound.crypto

import com.ethyllium.authservice.domain.port.driven.TotpSecretGenerator
import com.warrenstrange.googleauth.GoogleAuthenticator
import com.warrenstrange.googleauth.GoogleAuthenticatorKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TotpSecretAdapter(@Value("\${app.issuer}") private val issuer: String) : TotpSecretGenerator {
    override fun generateTotpSecret(email: String): Pair<String, String> {
        val key: GoogleAuthenticatorKey = GoogleAuthenticator().createCredentials()
        val secret = key.key
        val otpUri = buildOtpAuthUri(secret, issuer, email)
        return Pair(secret, otpUri)
    }

    private fun buildOtpAuthUri(secret: String, issuer: String, account: String): String {
        return "otpauth://totp/${issuer}:${account}?" + "secret=${secret}&" + "issuer=${issuer}&" + "algorithm=SHA1&" + "digits=6&" + "period=30"
    }
}