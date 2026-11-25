// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdGeneratorTest {

    private static final Instant NOW = Instant.parse("2023-11-14T22:13:20.123Z");

    private final AccountIdGenerator generator = new AccountIdGenerator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void makesAVersion7Id() {
        assertThat(generator.next().value().version()).isEqualTo(7);
    }

    @Test
    void putsTheClocksMillisecondsInTheFirst48Bits() {
        UUID id = generator.next().value();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void makesADifferentIdOnEachCall() {
        assertThat(generator.next()).isNotEqualTo(generator.next());
    }
}
