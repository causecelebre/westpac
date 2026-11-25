// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The settings for new account numbers.
 *
 * @param bank the two-digit bank code; the service numbers accounts for bank {@code 03} only
 */
@Validated
@ConfigurationProperties("westpac.account-number")
public record AccountNumberProperties(@NotBlank String bank) {}
