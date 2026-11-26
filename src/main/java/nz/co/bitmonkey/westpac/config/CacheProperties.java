// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The settings for the account cache.
 *
 * @param timeToLive how long a cached account stays in Redis
 */
@Validated
@ConfigurationProperties("westpac.cache")
public record CacheProperties(@NotNull Duration timeToLive) {}
