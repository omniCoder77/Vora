package com.ethyllium.authservice.infrastructure.outbound.postgres
import com.ethyllium.authservice.domain.model.User
import com.ethyllium.authservice.domain.port.driven.UserRepository
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.delete
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import reactor.test.StepVerifier
import java.util.*

@Testcontainers
class UserRepositoryImplTest {

    lateinit var template: R2dbcEntityTemplate
    lateinit var userRepository: UserRepository

    companion object {
        @Container
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("schema.sql")
    }

    @BeforeEach
    fun setUp() {
        template = R2dbcEntityTemplate(
            PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                    .host(postgres.host)
                    .port(postgres.firstMappedPort)
                    .username(postgres.username)
                    .password(postgres.password)
                    .database(postgres.databaseName)
                    .build()
            )
        )
        userRepository = UserRepositoryImpl(template)

        template.delete<com.ethyllium.authservice.infrastructure.outbound.postgres.entity.UserEntity>()
            .all()
            .block()
    }

    @Test
    fun `save should insert user and return userId`() {
        val user = User(UUID.randomUUID(), "test@example.com", )

        StepVerifier.create(userRepository.save(user, "hashedPassword123"))
            .expectNext(user.userId)
            .verifyComplete()
    }

    @Test
    fun `delete should remove user and return affected rows`() {
        val user = User(UUID.randomUUID(), "delete@example.com", )
        userRepository.save(user, "password").block()

        StepVerifier.create(userRepository.delete(user.userId))
            .expectNext(1L)
            .verifyComplete()
    }

    @Test
    fun `delete should return zero when user not found`() {
        val nonExistentId = UUID.randomUUID()

        StepVerifier.create(userRepository.delete(nonExistentId))
            .expectNext(0L)
            .verifyComplete()
    }

    @Test
    fun `findBy email should return matching user`() {
        val user = User(UUID.randomUUID(), "find@example.com", )
        userRepository.save(user, "password").block()

        StepVerifier.create(userRepository.findBy(email = "find@example.com", userId = null))
            .expectNextMatches { it.first.email == "find@example.com" }.verifyComplete()
    }

    @Test
    fun `findBy userId should return matching user`() {
        val user = User(UUID.randomUUID(), "findbyid@example.com")
        userRepository.save(user, "password").block()

        StepVerifier.create(userRepository.findBy(email = null, userId = user.userId))
            .expectNextMatches { it.first.userId == user.userId }
            .verifyComplete()
    }

    @Test
    fun `findBy name should return matching user`() {
        val user = User(UUID.randomUUID(), "findbyname@example.com")
        userRepository.save(user, "password").block()

        StepVerifier.create(userRepository.findBy(email = user.email, userId = user.userId))
            .expectNextMatches { it.first.email == user.email && it.first.userId == user.userId }
            .verifyComplete()
    }

    @Test
    fun `findBy with all null criteria should return error`() {
        StepVerifier.create(userRepository.findBy(email = null, userId = null))
            .expectError(IllegalArgumentException::class.java)
            .verify()
    }

    @Test
    fun `findBy with multiple criteria should return matching user`() {
        val user = User(UUID.randomUUID(), "multi@example.com")
        userRepository.save(user, "password").block()

        StepVerifier.create(userRepository.findBy(email = "multi@example.com", userId = null))
            .expectNextMatches { it.first.email == "multi@example.com" }
            .verifyComplete()
    }

    @Test
    fun `findBy should return empty when no match found`() {
        StepVerifier.create(userRepository.findBy(email = "nonexistent@example.com", userId = null))
            .verifyComplete()
    }
}