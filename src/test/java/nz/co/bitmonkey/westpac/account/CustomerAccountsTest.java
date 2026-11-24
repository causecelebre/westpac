// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerAccountsTest {

    private static List<AccountNumber> numbers(int count) {
        BranchNumber branch = new BranchNumber("0123");
        return IntStream.range(0, count)
                .mapToObj(suffix -> new AccountNumber("03", branch, "0000019", suffix))
                .toList();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5})
    void acceptsUpToFiveNumbers(int count) {
        List<AccountNumber> numbers = numbers(count);

        assertThat(new CustomerAccounts(numbers).numbers()).containsExactlyElementsOf(numbers);
    }

    @Test
    void refusesSixNumbers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CustomerAccounts(numbers(6)))
                .withMessage("a customer has at most 5 accounts, got 6");
    }

    @Test
    void aLaterChangeToTheCallersListDoesNotChangeTheRecord() {
        List<AccountNumber> callers = new ArrayList<>(numbers(2));
        CustomerAccounts accounts = new CustomerAccounts(callers);

        callers.add(numbers(3).get(2));

        assertThat(accounts.numbers()).containsExactlyElementsOf(numbers(2));
    }

    @Test
    void numbersRefusesAdd() {
        CustomerAccounts accounts = new CustomerAccounts(new ArrayList<>(numbers(2)));
        AccountNumber another = numbers(3).get(2);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> accounts.numbers().add(another));
    }
}
