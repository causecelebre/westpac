// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestFingerprintTest {

    private static final String SHA_256_OF_EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String FIRST_63 = SHA_256_OF_EMPTY.substring(0, 63);

    private static final CustomerId CUSTOMER = new CustomerId(UUID.fromString("7d3f9c2a-5b1e-4f6a-8c0d-2e9b4a7f1c35"));
    private static final BranchNumber BRANCH = new BranchNumber("0123");
    private static final CustomerName NAME = new CustomerName("Jane Citizen");
    private static final Nickname NICKNAME = new Nickname("Rainy Day Fund");

    private static final String WITH_NICKNAME = "11a50cf661905ca8b5c40b20c94a2dfc93b4186fa6e229d8d2115db9c2e43e8b";
    private static final String WITHOUT_NICKNAME = "1c610ec3d5ce2fc5e94e858d62172fb66e3d40fe8a7fadfae1e3449d7c916bd9";

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

    @Test
    void matchesTheHashComputedOutsideJavaWithANickname() {
        assertThat(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, NICKNAME).value())
                .isEqualTo(WITH_NICKNAME);
    }

    @Test
    void matchesTheHashComputedOutsideJavaWithoutANickname() {
        assertThat(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, null).value()).isEqualTo(WITHOUT_NICKNAME);
    }

    @Test
    void givesTheSameValueForTheSameInput() {
        assertThat(RequestFingerprint.of(
                        new CustomerId(CUSTOMER.value()),
                        new BranchNumber("0123"),
                        new CustomerName("Jane Citizen"),
                        new Nickname("Rainy Day Fund")))
                .isEqualTo(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, NICKNAME));
    }

    static Stream<Arguments> oneFieldChanged() {
        return Stream.of(
                Arguments.of(
                        "customer",
                        RequestFingerprint.of(
                                new CustomerId(UUID.fromString("7d3f9c2a-5b1e-4f6a-8c0d-2e9b4a7f1c36")),
                                BRANCH,
                                NAME,
                                NICKNAME)),
                Arguments.of("branch", RequestFingerprint.of(CUSTOMER, new BranchNumber("0124"), NAME, NICKNAME)),
                Arguments.of(
                        "name", RequestFingerprint.of(CUSTOMER, BRANCH, new CustomerName("Jane Citizens"), NICKNAME)),
                Arguments.of(
                        "nickname", RequestFingerprint.of(CUSTOMER, BRANCH, NAME, new Nickname("Rainy Day Funds"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneFieldChanged")
    void changesWhenOneFieldChanges(String field, RequestFingerprint changed) {
        assertThat(changed).isNotEqualTo(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, NICKNAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rainy Day Fund", "Holiday", "12345", "null null", "-----"})
    void givesADifferentValueForNoNicknameThanForEachNickname(String nickname) {
        assertThat(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, null))
                .isNotEqualTo(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, new Nickname(nickname)));
    }

    @Test
    void givesTheSameValueForANameThatNormalisesToTheSameName() {
        assertThat(RequestFingerprint.of(CUSTOMER, BRANCH, new CustomerName("Jane  Citizen"), NICKNAME))
                .isEqualTo(RequestFingerprint.of(CUSTOMER, BRANCH, NAME, NICKNAME));
    }
}
