package com.ethyllium.authservice.infrastructure.outbound.redis

import com.ethyllium.authservice.domain.port.driven.BloomFilterPort
import org.redisson.api.RBloomFilterReactive
import org.redisson.api.RedissonReactiveClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class RedisBloomFilterAdapter(private val redissonClient: RedissonReactiveClient) : BloomFilterPort {

    private val bloomFilter: RBloomFilterReactive<String> =
        redissonClient.getBloomFilter("user_emails_bloom_filter")

    fun ensureInit(): Mono<Boolean> {
        return bloomFilter.tryInit(1_000_000L, 0.01)
    }

    override fun addToFilter(userEmail: String): Mono<Boolean> {
        return bloomFilter.add(userEmail)
    }

    override fun contains(userEmail: String): Mono<Boolean> {
        val bloomFilter = redissonClient.getBloomFilter<String>("user_emails_bloom_filter")
        return bloomFilter.contains(userEmail)
    }

}