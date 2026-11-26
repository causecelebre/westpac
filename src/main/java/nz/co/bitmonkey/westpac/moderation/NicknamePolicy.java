// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.moderation;

import nz.co.bitmonkey.westpac.account.Nickname;

/** Decides whether a nickname is allowed on an account. */
public sealed interface NicknamePolicy permits ListNicknamePolicy {

    // TODO(#11): add a moderation service as a second implementation.

    /**
     * Tells whether a nickname is allowed.
     *
     * @param nickname the nickname to check
     * @return {@code true} if the nickname is allowed
     */
    boolean allows(Nickname nickname);
}
