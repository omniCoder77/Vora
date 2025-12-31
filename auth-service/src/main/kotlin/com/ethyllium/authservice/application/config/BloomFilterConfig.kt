package com.ethyllium.authservice.application.config

import com.ethyllium.authservice.infrastructure.outbound.redis.RedisBloomFilterAdapter
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BloomFilterConfig {

    @Bean
    fun paramsInit(service: RedisBloomFilterAdapter): ApplicationRunner {
        return ApplicationRunner {
            service.ensureInit().subscribe { created ->
                if (created) println("Bloom Filter created successfully")
                else println("Bloom Filter already exists")
            }
        }
    }
}