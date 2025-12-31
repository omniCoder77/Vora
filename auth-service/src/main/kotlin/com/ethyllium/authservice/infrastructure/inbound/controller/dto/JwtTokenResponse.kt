package com.ethyllium.authservice.infrastructure.inbound.controller.dto

data class JwtTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val idToken: String? = null // optional ID token for OpenID Connect, gives sub of jwt token, which will be user's ID
)