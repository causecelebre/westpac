// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The SHA-256 of a normalised create request, in lower-case hex. The service compares two fingerprints to tell a replay
 * of a request from a different request under the same idempotency key.
 *
 * @param value exactly 64 lower-case hex characters
 */
public record RequestFingerprint(String value) {

    private static final Pattern LOWER_CASE_HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private static final String SEPARATOR = "\u0000";

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

    /**
     * Makes the fingerprint of a create request from its normalised fields. The input is the customer id, the branch,
     * the customer name and the nickname, or an empty string for no nickname, joined with the NUL character, U+0000, as
     * UTF-8. The encoding must never change, because stored rows hold fingerprints made with it.
     */
    public static RequestFingerprint of(
            CustomerId customerId, BranchNumber branch, CustomerName customerName, @Nullable Nickname nickname) {
        String input = String.join(
                SEPARATOR,
                customerId.value().toString(),
                branch.value(),
                customerName.value(),
                Optional.ofNullable(nickname).map(Nickname::value).orElse(""));

        return new RequestFingerprint(
                HexFormat.of().formatHex(sha256().digest(input.getBytes(StandardCharsets.UTF_8))));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform must supply SHA-256", e);
        }
    }
}
