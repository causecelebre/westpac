// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.regex.Pattern;

/**
 * The four-digit branch part of an account number. It holds the shape only. Whether the bank owns the branch is a
 * separate rule, {@link Bank03#allows(BranchNumber)}.
 *
 * @param value exactly four ASCII digits, for example {@code 0123}
 */
public record BranchNumber(String value) {

    private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");

    /**
     * Makes a branch number from four ASCII digits.
     *
     * @throws IllegalArgumentException if {@code value} is not exactly four ASCII digits
     */
    public BranchNumber {
        if (!FOUR_DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException(String.format("branch must be four ASCII digits: %s", value));
        }
    }
}
