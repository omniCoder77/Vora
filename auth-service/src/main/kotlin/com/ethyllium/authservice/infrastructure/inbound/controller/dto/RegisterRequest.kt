package com.ethyllium.authservice.infrastructure.inbound.controller.dto

import com.ethyllium.authservice.domain.model.MFAOptions

data class RegisterRequest(
    val email: String,
    val password: String,
    val mfaOptions: MFAOptions = MFAOptions.NONE
)