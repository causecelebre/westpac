// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import org.jspecify.annotations.Nullable;

/**
 * A request to open a savings account, in domain types.
 *
 * @param customerId the customer who asks for the account
 * @param branch the branch to open the account at
 * @param name the customer's name
 * @param nickname the account's nickname, or {@code null} when the customer gave none
 * @param idempotency the idempotency key and the request fingerprint, or {@code null} when the request has no key
 */
public record OpenAccount(
        CustomerId customerId,
        BranchNumber branch,
        CustomerName name,
        @Nullable Nickname nickname,
        @Nullable Idempotency idempotency) {}
