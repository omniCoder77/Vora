package com.ethyllium.authservice.domain.port.driven

import reactor.core.publisher.Mono

interface BloomFilterPort {
    fun addToFilter(userEmail: String): Mono<Boolean>
    fun contains(userEmail: String): Mono<Boolean>
}