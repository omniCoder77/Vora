package com.ethyllium.authservice.domain.model

import java.util.UUID

data class User(
    val userId: UUID,
    val email: String,
    val mfaOptions: MFAOptions = MFAOptions.NONE,
    val mfaSecret: String? = null
)