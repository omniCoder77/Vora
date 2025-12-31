package com.ethyllium.authservice.infrastructure.inbound.controller.dto

import com.ethyllium.authservice.domain.model.MFAOptions

data class RegisterRequest(
    val password: String,
    val email: String,
    val mfaOptions: MFAOptions = MFAOptions.NONE
)