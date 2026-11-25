// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Defines the clock that every bean reads the time from. */
@Configuration(proxyBeanMethods = false)
public final class ClockConfig {

    private ClockConfig() {}

    /** Gives the system clock in UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
