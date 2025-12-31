package com.ethyllium.authservice.infrastructure.outbound.postgres.entity

import com.ethyllium.authservice.domain.model.MFAOptions
import com.ethyllium.authservice.domain.model.User
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

@Table("users")
data class UserEntity(
    @Id
    val userId: UUID,
    val email: String,
    val hashedPassword: String,
    val mfaOptions: String,
    val mfaSecret: String? // Nullable in DB
) {
    fun toModel() = User(
        userId = userId,
        email = email,
        mfaOptions = MFAOptions.valueOf(mfaOptions),
        mfaSecret = mfaSecret
    )
}

fun User.toEntity(hashedPassword: String) = UserEntity(
    userId = this.userId,
    email = this.email,
    hashedPassword = hashedPassword,
    mfaOptions = mfaOptions.name,
    mfaSecret = mfaSecret
)