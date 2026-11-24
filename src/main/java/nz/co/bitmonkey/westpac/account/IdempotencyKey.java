// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.UUID;

/** The client's {@code Idempotency-Key} header. Any UUID version is accepted. */
public record IdempotencyKey(UUID value) {}
