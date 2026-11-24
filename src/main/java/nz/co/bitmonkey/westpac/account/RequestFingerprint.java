// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.regex.Pattern;

/**
 * The SHA-256 of a normalised create request, in lower-case hex. The service compares two fingerprints to tell a replay
 * of a request from a different request under the same idempotency key.
 *
 * @param value exactly 64 lower-case hex characters
 */
public record RequestFingerprint(String value) {

    private static final Pattern LOWER_CASE_HEX_64 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Makes a fingerprint from 64 lower-case hex characters.
     *
     * @throws IllegalArgumentException if {@code value} is not exactly 64 lower-case hex characters
     */
    public RequestFingerprint {
        if (!LOWER_CASE_HEX_64.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    String.format("fingerprint must be 64 lower-case hex characters: %s", value));
        }
    }
}
