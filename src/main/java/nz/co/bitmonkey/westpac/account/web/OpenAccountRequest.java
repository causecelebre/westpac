// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import java.util.Optional;
import java.util.UUID;
import nz.co.bitmonkey.westpac.account.BranchNumber;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.CustomerName;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.account.OpenAccount;
import org.jspecify.annotations.Nullable;

/**
 * The JSON body of a request to open a savings account.
 *
 * @param customerId the customer who asks for the account
 * @param branch the branch to open the account at
 * @param customerName the customer's name
 * @param nickname the account's nickname, or {@code null} when the customer gave none
 */
record OpenAccountRequest(
        UUID customerId,
        String branch,
        String customerName,
        @Nullable String nickname) {

    /** Gives the request in domain types, with no idempotency key. */
    OpenAccount toOpenAccount() {
        return new OpenAccount(
                new CustomerId(customerId),
                new BranchNumber(branch),
                new CustomerName(customerName),
                Optional.ofNullable(nickname).map(Nickname::new).orElse(null),
                null);
    }
}
