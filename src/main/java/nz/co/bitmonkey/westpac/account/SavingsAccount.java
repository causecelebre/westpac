// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/**
 * One savings account, as the service stores it and returns it.
 *
 * @param id the account id
 * @param number the account number
 * @param customerId the customer who holds the account
 * @param name the customer's name
 * @param nickname the account's nickname, or {@code null} when the customer gave none
 * @param createdAt when the account was opened, truncated to microseconds, the precision of PostgreSQL
 *     {@code timestamptz}
 */
public record SavingsAccount(
        AccountId id,
        AccountNumber number,
        CustomerId customerId,
        CustomerName name,
        @Nullable Nickname nickname,
        Instant createdAt) {

    /** Makes the account, with {@code createdAt} truncated to microseconds. */
    public SavingsAccount {
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }
}
