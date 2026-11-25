// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

/** The outcome of a request to open a savings account. A fault that nobody planned is an exception, not a case. */
public sealed interface OpenResult {

    /**
     * The account was opened.
     *
     * @param account the new account
     */
    record Opened(SavingsAccount account) implements OpenResult {}

    /** The bank does not own the requested branch. No account was opened. */
    record BranchRefused() implements OpenResult {}

    /** The nickname holds a word from the list of offensive words. No account was opened. */
    record NicknameRefused() implements OpenResult {}

    /**
     * The customer already holds the most accounts allowed. No account was opened.
     *
     * @param limit the most accounts one customer can hold
     */
    record LimitReached(int limit) implements OpenResult {}
}
