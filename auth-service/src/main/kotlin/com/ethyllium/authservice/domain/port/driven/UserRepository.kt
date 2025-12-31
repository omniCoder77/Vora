package com.ethyllium.authservice.domain.port.driven

import com.ethyllium.authservice.domain.model.User
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface UserRepository {

    fun save(user: User, hashedPassword: String): Mono<UUID>
    fun delete(userId: UUID): Mono<Long>
    fun findBy(email: String? = null, userId: UUID? = null): Flux<Pair<User, String>>
    fun addSecret(secret: String, savedUserId: UUID): Mono<Long>
}