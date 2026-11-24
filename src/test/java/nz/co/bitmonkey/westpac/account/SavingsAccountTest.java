// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SavingsAccountTest {

    private static SavingsAccount openedAt(Instant createdAt) {
        return new SavingsAccount(
                new AccountId(UUID.fromString("01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00")),
                new AccountNumber("03", new BranchNumber("0123"), "0000019", 0),
                new CustomerId(UUID.fromString("7d3f9c2a-5b1e-4f6a-8c0d-2e9b4a7f1c35")),
                new CustomerName("Jane Citizen"),
                new Nickname("Rainy Day"),
                createdAt);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            2026-11-24T02:15:30.123456789Z, 2026-11-24T02:15:30.123456Z
            2026-11-24T02:15:30.123456999Z, 2026-11-24T02:15:30.123456Z
            2026-11-24T02:15:30.123456Z,    2026-11-24T02:15:30.123456Z
            2026-11-24T02:15:30Z,           2026-11-24T02:15:30Z
            """)
    void truncatesCreatedAtToMicroseconds(Instant given, Instant stored) {
        assertThat(openedAt(given).createdAt()).isEqualTo(stored);
    }

    @Test
    void buildsWithNoNickname() {
        SavingsAccount account = openedAt(Instant.parse("2026-11-24T02:15:30Z"));
        SavingsAccount withoutNickname = new SavingsAccount(
                account.id(), account.number(), account.customerId(), account.name(), null, account.createdAt());

        assertThat(withoutNickname.nickname()).isNull();
    }
}
