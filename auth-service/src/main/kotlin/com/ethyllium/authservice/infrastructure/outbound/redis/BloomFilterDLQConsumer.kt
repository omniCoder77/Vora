package com.ethyllium.authservice.infrastructure.outbound.redis

import com.ethyllium.authservice.domain.port.driven.BloomFilterPort
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class BloomFilterDLQConsumer(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val bloomFilterPort: BloomFilterPort,
    private val objectMapper: ObjectMapper,
    @Value("\${bloom.filter.dlq.max-retries:3}") private val maxRetries: Int
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    companion object {
        const val DLQ_STREAM_KEY = "bloom_filter:dlq"
        const val CONSUMER_GROUP = "bloom_filter_consumer_group"
        const val CONSUMER_NAME = "consumer-1"
        const val DEAD_LETTER_KEY = "bloom_filter:dead_letter"
    }
    
    @PostConstruct
    fun createConsumerGroup() {
        reactiveRedisTemplate.opsForStream<String, String>()
            .createGroup(DLQ_STREAM_KEY, CONSUMER_GROUP)
            .onErrorResume { 
                // Group might already exist
                logger.info("Consumer group already exists or stream doesn't exist yet")
                Mono.empty()
            }
            .subscribe()
    }
    
    @Scheduled(fixedDelay = 5000) // Process every 5 seconds
    fun processDLQ() {
        reactiveRedisTemplate.opsForStream<String, String>()
            .read(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(DLQ_STREAM_KEY, ReadOffset.lastConsumed())
            )
            .flatMap { message ->
                processMessage(message)
            }
            .subscribe()
    }
    
    private fun processMessage(message: MapRecord<String, String, String>): Mono<Void> {
        return try {
            val eventJson = message.value["data"] ?: return Mono.empty()
            val event = objectMapper.readValue(eventJson, BloomFilterDLQEvent::class.java)
            
            logger.info("Processing DLQ message for email: ${event.email}, retry: ${event.retryCount}")
            
            if (event.retryCount >= maxRetries) {
                return moveToDeadLetter(message, event)
            }
            
            bloomFilterPort.addToFilter(event.email)
                .flatMap {
                    // Success - acknowledge the message
                    logger.info("Successfully processed DLQ message for: ${event.email}")
                    acknowledgeMessage(message)
                }
                .onErrorResume { error ->
                    logger.warn("Retry ${event.retryCount + 1} failed for ${event.email}: ${error.message}")
                    requeueWithIncrementedRetry(message, event, error)
                }
        } catch (e: Exception) {
            logger.error("Failed to parse DLQ message", e)
            acknowledgeMessage(message) // Acknowledge to avoid reprocessing bad data
        }
    }
    
    private fun acknowledgeMessage(message: MapRecord<String, String, String>): Mono<Void> {
        return reactiveRedisTemplate.opsForStream<String, String>()
            .acknowledge(CONSUMER_GROUP, message)
            .then(
                // Delete the message from stream
                reactiveRedisTemplate.opsForStream<String, String>()
                    .delete(message)
                    .then()
            )
    }
    
    private fun requeueWithIncrementedRetry(
        message: MapRecord<String, String, String>,
        event: BloomFilterDLQEvent,
        error: Throwable
    ): Mono<Void> {
        val updatedEvent = event.copy(
            retryCount = event.retryCount + 1,
            errorMessage = error.message,
            timestamp = Instant.now()
        )
        
        return try {
            val eventJson = objectMapper.writeValueAsString(updatedEvent)
            
            reactiveRedisTemplate.opsForStream<String, String>()
                .add(DLQ_STREAM_KEY, mapOf("data" to eventJson))
                .flatMap {
                    acknowledgeMessage(message)
                }
        } catch (e: Exception) {
            logger.error("Failed to requeue message", e)
            Mono.empty()
        }
    }
    
    private fun moveToDeadLetter(
        message: MapRecord<String, String, String>,
        event: BloomFilterDLQEvent
    ): Mono<Void> {
        logger.error("Max retries exceeded for email: ${event.email}, moving to dead letter")
        
        return try {
            val eventJson = objectMapper.writeValueAsString(event)
            
            reactiveRedisTemplate.opsForList()
                .rightPush(DEAD_LETTER_KEY, eventJson)
                .flatMap {
                    acknowledgeMessage(message)
                }
        } catch (e: Exception) {
            logger.error("Failed to move to dead letter", e)
            Mono.empty()
        }
    }
}