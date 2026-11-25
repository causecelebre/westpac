// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * The settings for nickname moderation.
 *
 * @param listLocation the location of the list of offensive words, such as a {@code classpath:} or {@code file:} URL
 */
@Validated
@ConfigurationProperties("westpac.moderation")
public record ModerationProperties(@NotNull Resource listLocation) {}
