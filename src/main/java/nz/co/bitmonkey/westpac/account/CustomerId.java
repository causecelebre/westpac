// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.UUID;

/**
 * Identifies the customer who owns an account. The system that owns customers issues it, so any UUID version is
 * accepted.
 */
public record CustomerId(UUID value) {}
