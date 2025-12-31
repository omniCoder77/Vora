package com.ethyllium.authservice.infrastructure.outbound.redis

import java.time.Instant

data class BloomFilterDLQEvent(
    val email: String,
    val timestamp: Instant = Instant.now(),
    val retryCount: Int = 0,
    val errorMessage: String? = null
)