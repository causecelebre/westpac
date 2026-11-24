// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RequestFingerprintTest {

    private static final String SHA_256_OF_EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String FIRST_63 = SHA_256_OF_EMPTY.substring(0, 63);

    @Test
    void accepts64LowerCaseHexCharacters() {
        assertThat(new RequestFingerprint(SHA_256_OF_EMPTY).value()).isEqualTo(SHA_256_OF_EMPTY);
    }

    @Test
    void acceptsEveryHexDigit() {
        String value = "0123456789abcdef".repeat(4);

        assertThat(new RequestFingerprint(value).value()).isEqualTo(value);
    }

    static Stream<String> refused() {
        return Stream.of(
                "",
                FIRST_63,
                SHA_256_OF_EMPTY + "0",
                "E" + SHA_256_OF_EMPTY.substring(1),
                FIRST_63 + "g",
                FIRST_63 + " ",
                FIRST_63 + "٣");
    }

    @ParameterizedTest
    @MethodSource("refused")
    void refusesAnythingBut64LowerCaseHexCharacters(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RequestFingerprint(value))
                .withMessage("fingerprint must be 64 lower-case hex characters: %s", value);
    }
}
