// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import nz.co.bitmonkey.westpac.account.SavingsAccount;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

/** Turns on caching, defines how accounts are stored in Redis, and lets calls go on when Redis fails. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public final class CacheConfig implements CachingConfigurer {

    private final LoggingCacheErrorHandler errorHandler;

    /**
     * Makes the configuration.
     *
     * @param errorHandler the handler that logs and counts a failed get or put
     */
    CacheConfig(LoggingCacheErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /** Gives the handler that logs and counts a failed get or put, then goes on without the cache. */
    @Override
    public CacheErrorHandler errorHandler() {
        return errorHandler;
    }

    /**
     * Gives the Redis settings for every cache. Each key starts with {@code westpac:v1:} and the cache name. Each value
     * is JSON typed to {@link SavingsAccount}, with no type information in it.
     */
    @Bean
    RedisCacheConfiguration redisCacheConfiguration(CacheProperties properties) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.timeToLive())
                .computePrefixWith(name -> "westpac:v1:" + name + ":")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(JsonMapper.builder().build(), SavingsAccount.class)));
    }

    /**
     * Makes each cache put wait for Redis. A failed put then reaches {@link #errorHandler()}, which logs and counts it.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer immediateCacheWrites(RedisConnectionFactory connectionFactory) {
        return builder ->
                builder.cacheWriter(RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites()));
    }
}
