package com.ethyllium.authservice.infrastructure.inbound.controller.dto

import com.fasterxml.jackson.annotation.JsonProperty

class LoginRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) var password: CharArray,
    val email: String
) {

    fun clear() {
        password.fill('\u0000')
    }

    override fun toString(): String {
        return "LoginRequest(email='$email')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoginRequest

        if (!password.contentEquals(other.password)) return false
        if (email != other.email) return false

        return true
    }

    override fun hashCode(): Int {
        val result = 31 * email.hashCode()
        return result
    }
}