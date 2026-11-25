// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.account.SavingsAccount;
import org.jspecify.annotations.Nullable;

/**
 * The JSON body that describes one savings account.
 *
 * @param id the account id
 * @param accountNumber the account number in its standard form
 * @param customerId the customer who holds the account
 * @param customerName the customer's name
 * @param nickname the account's nickname, or {@code null} when it has none
 * @param createdAt when the account was opened
 */
record AccountResponse(
        UUID id,
        String accountNumber,
        UUID customerId,
        String customerName,
        @Nullable String nickname,
        Instant createdAt) {

    /** Describes {@code account}. */
    static AccountResponse of(SavingsAccount account) {
        return new AccountResponse(
                account.id().value(),
                account.number().toString(),
                account.customerId().value(),
                account.name().value(),
                Optional.ofNullable(account.nickname()).map(Nickname::value).orElse(null),
                account.createdAt());
    }
}
