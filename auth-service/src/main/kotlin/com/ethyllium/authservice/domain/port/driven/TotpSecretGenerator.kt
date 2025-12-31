package com.ethyllium.authservice.domain.port.driven

interface TotpSecretGenerator {

    /**
     * Generates a TOTP secret and its corresponding OTP Auth URI for the given email.
     * @param email The email address associated with the TOTP secret.
     * @return A pair containing the TOTP secret and the OTP Auth URI. First element is the secret, second is the URI.
     */
    fun generateTotpSecret(email: String): Pair<String, String>
}
