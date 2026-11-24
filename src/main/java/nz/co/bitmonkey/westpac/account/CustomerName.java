// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.regex.Pattern;

/**
 * The customer's name as stored on an account, in the form {@link TextNormaliser} gives. It holds 1 to 100 code points:
 * letters, combining marks, spaces, hyphens, apostrophes and full stops, with at least one letter.
 */
public record CustomerName(String value) {

    /** The characters a name may hold, with at least one letter. The request record uses it too. */
    public static final String PATTERN = "(?=.*\\p{L})[\\p{L}\\p{M} '’.-]+";

    private static final Pattern ALLOWED = Pattern.compile(PATTERN);
    private static final int MAX_CODE_POINTS = 100;

    /**
     * Makes a customer name from a value, which it normalises first. The messages never contain the name.
     *
     * @throws IllegalArgumentException if the normalised value is not 1 to 100 code points, or holds a character that
     *     is not allowed, or holds no letter
     */
    public CustomerName {
        value = TextNormaliser.normalise(value);
        int codePoints = value.codePointCount(0, value.length());

        if (codePoints < 1 || codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException(
                    String.format("customer name must be 1 to 100 code points, got %s", codePoints));
        }

        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "customer name must hold only letters, marks, spaces, hyphens, apostrophes and full stops,"
                            + " with at least one letter");
        }
    }
}
