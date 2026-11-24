// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AccountNumberTest {

    private static final int BRANCH = 0;
    private static final int BASE = 4;
    private static final int SUFFIX = 11;

    @ParameterizedTest
    @CsvSource(textBlock = """
            0123, 0000019
            1501, 0000038
            1501, 0989990
            1501, 0990008
            1501, 5000019
            """)
    void acceptsValidNumbers(String branch, String base) {
        AccountNumber number = new AccountNumber("03", new BranchNumber(branch), base, 0);

        assertThat(number.base()).isEqualTo(base);
    }

    @Test
    void refusesAWrongCheckDigitWithoutNamingTheBase() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountNumber("03", new BranchNumber("1501"), "0000019", 0))
                .withMessage("account number check digit is wrong, at bank 03 branch 1501");
    }

    static Stream<Arguments> weightedChanges() {
        return Stream.of(
                        changes("01230000019000", range(BRANCH, BASE), range(BASE + 1, SUFFIX)),
                        changes("15010000038000", range(BRANCH, BASE), range(BASE + 1, SUFFIX)),
                        changes("15015000019000", range(BASE + 1, SUFFIX)))
                .flatMap(s -> s)
                .map(AccountNumberTest::parts);
    }

    @ParameterizedTest
    @MethodSource("weightedChanges")
    void refusesEachChangeInAWeightedPosition(String branch, String base, int suffix) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountNumber("03", new BranchNumber(branch), base, suffix))
                .withMessageContaining("check digit");
    }

    static Stream<Arguments> zeroWeightChanges() {
        return Stream.of(
                        changes("01230000019000", range(SUFFIX, SUFFIX + 3)),
                        changes("15010000038000", range(SUFFIX, SUFFIX + 3)),
                        changes("15015000019000", range(BRANCH, BASE), range(SUFFIX, SUFFIX + 3)),
                        changes("15015000019000", new int[] {BASE}).filter(digits -> digits.charAt(BASE) != '0'))
                .flatMap(s -> s)
                .map(AccountNumberTest::parts);
    }

    @ParameterizedTest
    @MethodSource("zeroWeightChanges")
    void acceptsEachChangeInAZeroWeightPosition(String branch, String base, int suffix) {
        AccountNumber number = new AccountNumber("03", new BranchNumber(branch), base, suffix);

        assertThat(number.suffix()).isEqualTo(suffix);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            01, 0000019, 0, 'account number bank must be 03, got 01'
            3, 0000019, 0, 'account number bank must be 03, got 3'
            03, 000019, 0, 'account number base must be seven ASCII digits, at bank 03 branch 0123'
            03, 00000019, 0, 'account number base must be seven ASCII digits, at bank 03 branch 0123'
            03, 000001٩, 0, 'account number base must be seven ASCII digits, at bank 03 branch 0123'
            03, 0000019, -1, 'account number suffix must be 0 to 999, got -1 at bank 03 branch 0123'
            03, 0000019, 1000, 'account number suffix must be 0 to 999, got 1000 at bank 03 branch 0123'
            """)
    void refusesABadShape(String bank, String base, int suffix, String message) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccountNumber(bank, new BranchNumber("0123"), base, suffix))
                .withMessage(message);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0123, 0000019, 0, 03-0123-0000019-000
            1501, 0000038, 4, 03-1501-0000038-004
            1501, 5000019, 999, 03-1501-5000019-999
            """)
    void givesTheStandardForm(String branch, String base, int suffix, String form) {
        assertThat(new AccountNumber("03", new BranchNumber(branch), base, suffix))
                .hasToString(form);
    }

    private static int[] range(int from, int to) {
        return IntStream.range(from, to).toArray();
    }

    private static Stream<String> changes(String digits, int[]... positionSets) {
        return Stream.of(positionSets)
                .flatMapToInt(IntStream::of)
                .boxed()
                .flatMap(position -> IntStream.rangeClosed('0', '9')
                        .filter(digit -> digit != digits.charAt(position))
                        .mapToObj(digit -> {
                            StringBuilder changed = new StringBuilder(digits);
                            changed.setCharAt(position, (char) digit);
                            return changed.toString();
                        }));
    }

    private static Arguments parts(String digits) {
        return Arguments.of(
                digits.substring(BRANCH, BASE),
                digits.substring(BASE, SUFFIX),
                Integer.parseInt(digits.substring(SUFFIX)));
    }
}
