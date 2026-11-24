// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BranchNumberTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            0000, false
            0001, true
            0999, true
            1000, false
            1299, false
            1300, true
            1399, true
            1400, false
            1499, false
            1500, true
            1599, true
            1600, false
            1699, false
            1700, true
            1799, true
            1800, false
            1899, false
            1900, true
            1999, true
            2000, false
            7349, false
            7350, true
            7399, true
            7400, false
            9999, false
            """)
    void bank03AllowsOnlyItsRanges(String value, boolean allowed) {
        assertThat(Bank03.allows(new BranchNumber(value))).isEqualTo(allowed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "12345", "12a4", " 123", "١٢٣٤"})
    void refusesAnythingButFourAsciiDigits(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BranchNumber(value))
                .withMessage("branch must be four ASCII digits: %s", value);
    }
}
