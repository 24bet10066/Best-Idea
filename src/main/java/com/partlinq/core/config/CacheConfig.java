package com.partlinq.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the PartLinQ application.
 * Uses a single Caffeine CacheManager with named caches and configurable TTLs.
 *
 * Consolidates all caches under one manager to avoid Spring's
 * NoUniqueBeanDefinitionException when multiple CacheManagers exist.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Primary cache manager with all named caches.
     * Default: 1000 entries max, 10-minute TTL.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "technicians",
                "shops",
                "spareParts",
                "inventory",
                "trustScores",
                "creditScores",
                "partSearch",
                "partAutocomplete",
                "orders"
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        return cacheManager;
    }
}
