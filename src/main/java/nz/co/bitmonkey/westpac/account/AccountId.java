// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.UUID;

/**
 * Identifies one savings account. The service generates it, and it is always a UUID version 7, so new ids sort by the
 * time they were made.
 *
 * @param value the UUID; its version must be 7
 */
public record AccountId(UUID value) {

    private static final int VERSION = 7;

    /**
     * Makes an account id from a version 7 UUID.
     *
     * @throws IllegalArgumentException if {@code value} is not version 7
     */
    public AccountId {
        if (value.version() != VERSION) {
            throw new IllegalArgumentException(
                    String.format("account id must be UUID version 7, got version %s: %s", value.version(), value));
        }
    }
}
