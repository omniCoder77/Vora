package com.ethyllium.authservice.infrastructure.outbound.redis

import com.ethyllium.authservice.domain.port.driven.OtpStoragePort
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class RedisOtpAdapter(
    private val redisTemplate: ReactiveStringRedisTemplate
) : OtpStoragePort {

    companion object {
        private const val OTP_PREFIX = "auth:otp:"
        private const val ATTEMPT_PREFIX = "auth:otp:attempts:"
        private val OTP_TTL = Duration.ofMinutes(5)
    }

    override fun saveOtp(email: String, otp: String): Mono<Boolean> {
        val key = "$OTP_PREFIX$email"
        val resetAttempts = redisTemplate.delete("$ATTEMPT_PREFIX$email")
        
        return resetAttempts.then(
            redisTemplate.opsForValue()
                .set(key, otp, OTP_TTL)
        )
    }

    override fun getOtp(email: String): Mono<String> {
        return redisTemplate.opsForValue().get("$OTP_PREFIX$email")
    }

    override fun deleteOtp(email: String): Mono<Long> {
        return redisTemplate.delete("$OTP_PREFIX$email")
            .flatMap { redisTemplate.delete("$ATTEMPT_PREFIX$email") }
    }

    override fun incrementAttempts(email: String): Mono<Long> {
        val key = "$ATTEMPT_PREFIX$email"
        return redisTemplate.opsForValue().increment(key)
            .flatMap { count ->
                if (count == 1L) {
                    redisTemplate.expire(key, OTP_TTL).map { count }
                } else {
                    Mono.just(count)
                }
            }
    }
}