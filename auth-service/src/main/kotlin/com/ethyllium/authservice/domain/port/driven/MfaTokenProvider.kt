package com.ethyllium.authservice.domain.port.driven

interface MfaTokenProvider {
    fun generateSecret(): String
    fun validateCode(secret: String, code: Int): Boolean
    fun getQrCodeUri(secret: String, accountName: String): String
}