package com.ethyllium.authservice.infrastructure.outbound.redis

import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration

class RedisOtpAdapterTest {

    private val redisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val valueOps = mockk<ReactiveValueOperations<String, String>>()
    private val adapter = RedisOtpAdapter(redisTemplate)

    @Test
    fun `incrementAttempts - should set expiry on first attempt`() {
        val email = "test@test.com"
        val key = "auth:otp:attempts:$email"

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.increment(key) } returns Mono.just(1L) // First increment
        every { redisTemplate.expire(key, any<Duration>()) } returns Mono.just(true)

        StepVerifier.create(adapter.incrementAttempts(email))
            .expectNext(1L)
            .verifyComplete()

        verify { redisTemplate.expire(key, any<Duration>()) }
    }
}