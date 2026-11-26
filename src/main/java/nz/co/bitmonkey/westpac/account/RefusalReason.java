// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

/** The business rules that can refuse a create, each with the code that the {@code reason} metric tag carries. */
enum RefusalReason {
    BRANCH("branch"),
    NICKNAME("nickname"),
    LIMIT("limit"),
    KEY_REUSED("key-reused");

    private final String code;

    RefusalReason(String code) {
        this.code = code;
    }

    /** Gives the code as the {@code reason} tag carries it, for example {@code key-reused}. */
    String code() {
        return code;
    }
}
