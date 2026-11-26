// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import nz.co.bitmonkey.westpac.account.SavingsAccount;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

/** Turns on caching and defines how accounts are stored in Redis. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public final class CacheConfig {

    private CacheConfig() {}

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
}
