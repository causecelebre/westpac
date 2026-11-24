// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerNameTest {

    private static final String CHARACTER_RULE =
            "customer name must hold only letters, marks, spaces, hyphens, apostrophes and full stops,"
                    + " with at least one letter";

    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    static Stream<Arguments> normalised() {
        return Stream.of(
                Arguments.of(" Jane   Citizen ", "Jane Citizen"),
                Arguments.of("\tJane Citizen\n", "Jane Citizen"),
                Arguments.of("Rene" + cp(0x301), "Ren" + cp(0xe9)),
                Arguments.of("O’Brien", "O’Brien"),
                Arguments.of("O'Brien", "O'Brien"),
                Arguments.of("Smith-Jones", "Smith-Jones"),
                Arguments.of("St. John", "St. John"),
                Arguments.of("Te Rangi" + cp(0x101), "Te Rangi" + cp(0x101)));
    }

    @ParameterizedTest
    @MethodSource("normalised")
    void storesTheNormalisedForm(String given, String stored) {
        CustomerName name = new CustomerName(given);

        assertThat(name.value()).isEqualTo(stored);
        assertThat(new CustomerName(name.value()).value()).isEqualTo(stored);
    }

    static Stream<String> longestAllowed() {
        return Stream.of("a".repeat(100), "a".repeat(99) + cp(0x10437), "a".repeat(99) + "e" + cp(0x301));
    }

    @ParameterizedTest
    @MethodSource("longestAllowed")
    void accepts100CodePoints(String value) {
        CustomerName name = new CustomerName(value);

        assertThat(name.value().codePointCount(0, name.value().length())).isEqualTo(100);
    }

    @Test
    void refuses101CodePoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CustomerName("a".repeat(101)))
                .withMessage("customer name must be 1 to 100 code points, got 101");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void refusesABlankValue(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CustomerName(value))
                .withMessage("customer name must be 1 to 100 code points, got 0");
    }

    static Stream<String> refusedCharacters() {
        return Stream.of(
                "Jane\tCitizen",
                "Jane\nCitizen",
                "Jane Citizen2",
                "Jane " + cp(0x1f600),
                "Jane" + cp(0x200d) + "Citizen",
                "Jane" + cp(0x202e) + "Citizen",
                "Jane" + cp(0xa0) + "Citizen",
                cp(0xa0) + "Jane",
                "Jane_Citizen",
                "Jane & Citizen",
                "- '",
                "'’.-");
    }

    @ParameterizedTest
    @MethodSource("refusedCharacters")
    void refusesACharacterOutsideTheRuleOrANameWithNoLetter(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CustomerName(value))
                .withMessage(CHARACTER_RULE);
    }
}
