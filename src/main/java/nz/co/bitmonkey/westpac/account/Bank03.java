// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.List;

/**
 * What the service knows about bank {@code 03}, the one bank it numbers accounts for.
 *
 * <p><b>Source.</b> The branch ranges are from Inland Revenue, <i>Payroll Calculations &amp; Business Rules
 * Specification, 1 April 2020 to 31 March 2021</i>, V1.0, 13 December 2019, the last edition with a range table.
 * Implemented as published, with no variation.
 */
final class Bank03 {

    /** The bank code. */
    static final String CODE = "03";

    private static final List<Range> BRANCHES = List.of(
            new Range(1, 999),
            new Range(1300, 1399),
            new Range(1500, 1599),
            new Range(1700, 1799),
            new Range(1900, 1999),
            new Range(7350, 7399));

    private Bank03() {}

    /**
     * Tells whether the bank owns a branch.
     *
     * @param branch the branch to look up
     * @return {@code true} if {@code branch} is inside one of the bank's ranges
     */
    static boolean allows(BranchNumber branch) {
        int number = Integer.parseInt(branch.value());
        return BRANCHES.stream().anyMatch(range -> range.contains(number));
    }

    private record Range(int first, int last) {

        boolean contains(int number) {
            return number >= first && number <= last;
        }
    }
}
