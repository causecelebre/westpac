// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

/**
 * Logs and counts a failed cache get or put, then lets the call go on without the cache. A failed get is a miss, so the
 * method reads the database. A failed evict or clear is thrown again, because nothing evicts or clears.
 */
@Component
final class LoggingCacheErrorHandler implements CacheErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    private final Counter getErrors;

    private final Counter putErrors;

    /**
     * Makes the handler and registers its {@code westpac.cache.errors} counters, one for each operation.
     *
     * @param registry the registry that holds the counters
     */
    LoggingCacheErrorHandler(MeterRegistry registry) {
        getErrors = errorCounter(registry, CacheOperation.GET);
        putErrors = errorCounter(registry, CacheOperation.PUT);
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        skip(CacheOperation.GET, getErrors, exception, cache, key);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
        skip(CacheOperation.PUT, putErrors, exception, cache, key);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        throw exception;
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        throw exception;
    }

    private static Counter errorCounter(MeterRegistry registry, CacheOperation operation) {
        return Counter.builder("westpac.cache.errors")
                .description("Cache operations that failed and were skipped")
                .tag("operation", operation.code())
                .register(registry);
    }

    private static void skip(
            CacheOperation operation, Counter errors, RuntimeException exception, Cache cache, Object key) {
        LOG.warn(
                "Cache {} failed, so the call goes on without the cache: cache={} key={}",
                operation.code(),
                cache.getName(),
                key,
                exception);
        errors.increment();
    }
}
