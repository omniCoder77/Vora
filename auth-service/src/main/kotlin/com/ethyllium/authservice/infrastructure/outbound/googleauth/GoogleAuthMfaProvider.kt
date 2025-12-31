package com.ethyllium.authservice.infrastructure.outbound.googleauth

import com.ethyllium.authservice.domain.port.driven.MfaTokenProvider
import com.warrenstrange.googleauth.GoogleAuthenticator
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleAuthMfaProvider(
    @Value("\${app.issuer}") private val issuer: String
) : MfaTokenProvider {

    private val gAuth: GoogleAuthenticator = GoogleAuthenticator(
        GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().build()
    )

    override fun generateSecret(): String {
        return gAuth.createCredentials().key
    }

    override fun validateCode(secret: String, code: Int): Boolean {
        return gAuth.authorize(secret, code)
    }

    override fun getQrCodeUri(secret: String, accountName: String): String {
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(issuer, accountName, gAuth.createCredentials(secret))
    }
}