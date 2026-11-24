// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AccountIdTest {

    @Test
    void acceptsVersion7() {
        UUID value = UUID.fromString("01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00");

        assertThat(new AccountId(value).value()).isEqualTo(value);
    }

    static Stream<UUID> otherVersions() {
        return Stream.of(
                        "c232ab00-9414-11ec-b3c8-9e6bdeced846",
                        "7d3f9c2a-5b1e-4f6a-8c0d-2e9b4a7f1c35",
                        "1ec9414c-232a-6b00-b3c8-9e6bdeced846")
                .map(UUID::fromString);
    }

    @ParameterizedTest
    @MethodSource("otherVersions")
    void refusesOtherVersions(UUID value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountId(value))
                .withMessageContaining("version " + value.version());
    }
}
