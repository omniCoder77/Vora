package com.ethyllium.authservice.infrastructure.outbound.postgres

import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.model.User
import com.ethyllium.authservice.domain.port.driven.UserRepository
import com.ethyllium.authservice.infrastructure.outbound.postgres.entity.UserEntity
import com.ethyllium.authservice.infrastructure.outbound.postgres.entity.toEntity
import org.slf4j.LoggerFactory
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@Repository
class UserRepositoryImpl(private val r2dbcEntityTemplate: R2dbcEntityTemplate) : UserRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(user: User, hashedPassword: String): Mono<UUID> {
        return r2dbcEntityTemplate.insert(user.toEntity(hashedPassword)).map { it.userId }
    }

    override fun delete(userId: UUID): Mono<Long> {
        val query = Query.query(Criteria.where("user_id").`is`(userId))
        return r2dbcEntityTemplate.delete(query, UserEntity::class.java)
    }

    override fun findBy(
        email: String?,
        userId: UUID?
    ): Flux<Pair<User, String>> {
        val criteria = mutableListOf<Criteria>()
        email?.let { if (it.isNotEmpty() && it.isNotBlank()) criteria.add(Criteria.where("email").`is`(it)) }
        userId?.let { criteria.add(Criteria.where("user_id").`is`(it)) }
        if (criteria.isEmpty()) {
            logger.warn("All criteria are null in findBy method. This will return all users in the database.")
            return Flux.error(IllegalArgumentException("At least one search criterion (email, userId) must be provided."))
        }
        val query = Query.query(criteria.reduce { acc, currentCriteria -> acc.and(currentCriteria) })

        return r2dbcEntityTemplate.select(query, UserEntity::class.java)
            .map { Pair(User(it.userId, it.email, MFAOptions.valueOf(it.mfaOptions)), it.hashedPassword) }
    }

    override fun addSecret(secret: String, savedUserId: UUID): Mono<Long> {
        val query = Query.query(Criteria.where("user_id").`is`(savedUserId))
        val update = Update.update("mfa_secret", secret)
        return r2dbcEntityTemplate.update(query, update, UserEntity::class.java)
    }
}