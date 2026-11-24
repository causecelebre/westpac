// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.List;

/**
 * The account numbers one customer holds. The service reads it to check the account limit and to choose the suffix of a
 * new account.
 *
 * @param numbers the customer's account numbers, at most {@link CustomerAccountLimit#MAX}; the record holds an
 *     unmodifiable copy
 */
public record CustomerAccounts(List<AccountNumber> numbers) {

    /**
     * Makes the record from a copy of {@code numbers}.
     *
     * @throws IllegalArgumentException if {@code numbers} has more than {@link CustomerAccountLimit#MAX} entries
     */
    public CustomerAccounts {
        if (numbers.size() > CustomerAccountLimit.MAX) {
            throw new IllegalArgumentException(String.format(
                    "a customer has at most %s accounts, got %s", CustomerAccountLimit.MAX, numbers.size()));
        }
        numbers = List.copyOf(numbers);
    }
}
