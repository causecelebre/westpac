// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

/**
 * The idempotency key of a create request and the fingerprint of that request. They travel together, so a request has
 * both or neither.
 *
 * @param key the client's {@code Idempotency-Key} header
 * @param fingerprint the fingerprint of the request that came with the key
 */
public record Idempotency(IdempotencyKey key, RequestFingerprint fingerprint) {}
