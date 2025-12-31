package com.ethyllium.authservice.infrastructure.outbound.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class BloomFilterDLQPublisher(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    companion object {
        const val DLQ_STREAM_KEY = "bloom_filter:dlq"
    }

    fun publishToDLQ(event: BloomFilterDLQEvent): Mono<RecordId> {
        return try {
            val eventJson = objectMapper.writeValueAsString(event)
            
            reactiveRedisTemplate.opsForStream<String, String>()
                .add(
                    DLQ_STREAM_KEY,
                    mapOf("data" to eventJson)
                )
                .doOnSuccess { recordId ->
                    logger.info("Published event to DLQ: $recordId, email: ${event.email}")
                }
                .doOnError { error ->
                    logger.error("Failed to publish to DLQ for email: ${event.email}", error)
                }
        } catch (e: Exception) {
            logger.error("Failed to serialize DLQ event", e)
            Mono.error(e)
        }
    }
}