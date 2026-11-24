// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

/**
 * An account found by its idempotency key, with the fingerprint of the request that opened it. The service compares the
 * fingerprint with a new request's to tell a replay from a different request.
 *
 * @param account the account
 * @param fingerprint the fingerprint of the request that opened the account
 */
public record StoredAccount(SavingsAccount account, RequestFingerprint fingerprint) {}
