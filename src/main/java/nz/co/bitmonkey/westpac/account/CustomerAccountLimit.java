// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

/**
 * The most savings accounts one customer can hold. The database check constraint holds the same bound, and a test
 * compares the two.
 */
final class CustomerAccountLimit {

    /** The most accounts for one customer. */
    static final int MAX = 5;

    private CustomerAccountLimit() {}
}
