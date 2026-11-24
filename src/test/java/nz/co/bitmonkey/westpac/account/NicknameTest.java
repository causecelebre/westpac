// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameTest {

    private static final String CHARACTER_RULE =
            "nickname must hold only letters, marks, digits, spaces, hyphens, apostrophes, full stops and &";

    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
                Arguments.of("Rainy Day", "Rainy Day"),
                Arguments.of("  Rainy    Day  ", "Rainy Day"),
                Arguments.of("Car fund 2027", "Car fund 2027"),
                Arguments.of("Bills & Rent", "Bills & Rent"),
                Arguments.of("Mum’s fund", "Mum’s fund"),
                Arguments.of("St. Kilda-trip", "St. Kilda-trip"),
                Arguments.of("Save " + cp(0xbd) + " " + cp(0x663), "Save " + cp(0xbd) + " " + cp(0x663)),
                Arguments.of("Cafe" + cp(0x301) + " fund", "Caf" + cp(0xe9) + " fund"),
                Arguments.of("a".repeat(5), "a".repeat(5)),
                Arguments.of("a".repeat(30), "a".repeat(30)),
                Arguments.of("a".repeat(29) + cp(0x10437), "a".repeat(29) + cp(0x10437)));
    }

    @ParameterizedTest
    @MethodSource("accepted")
    void storesTheNormalisedForm(String given, String stored) {
        Nickname nickname = new Nickname(given);

        assertThat(nickname.value()).isEqualTo(stored);
        assertThat(new Nickname(nickname.value()).value()).isEqualTo(stored);
    }

    static Stream<Arguments> wrongLength() {
        return Stream.of(
                Arguments.of("", 0),
                Arguments.of("     ", 0),
                Arguments.of("\t\n", 0),
                Arguments.of("a".repeat(4), 4),
                Arguments.of("  abcd  ", 4),
                Arguments.of("a".repeat(31), 31));
    }

    @ParameterizedTest
    @MethodSource("wrongLength")
    void refusesALengthOutside5To30(String value, int codePoints) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Nickname(value))
                .withMessage("nickname must be 5 to 30 code points, got %s", codePoints);
    }

    static Stream<String> refusedCharacters() {
        return Stream.of(
                "Rainy\tDay",
                "Rainy\nDay",
                "Rainy " + cp(0x1f600),
                "Rainy" + cp(0x200d) + "Day",
                "Rainy" + cp(0x202e) + "Day",
                "Rainy" + cp(0xa0) + "Day",
                "Rainy_Day",
                "Rainy $ Day");
    }

    @ParameterizedTest
    @MethodSource("refusedCharacters")
    void refusesACharacterOutsideTheRule(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Nickname(value))
                .withMessage(CHARACTER_RULE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-----", "12345"})
    void needsNoLetter(String value) {
        assertThat(new Nickname(value).value()).isEqualTo(value);
    }
}
