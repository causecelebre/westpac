// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.regex.Pattern;

/**
 * The customer's own name for an account, in the form {@link TextNormaliser} gives. It holds 5 to 30 code points:
 * letters, combining marks, digits, spaces, hyphens, apostrophes, full stops and {@code &}. An account with no nickname
 * has none, not an empty one.
 */
public record Nickname(String value) {

    /** The characters a nickname may hold. The request record uses it too. */
    public static final String PATTERN = "[\\p{L}\\p{M}\\p{N} '’.&-]+";

    private static final Pattern ALLOWED = Pattern.compile(PATTERN);
    private static final int MIN_CODE_POINTS = 5;
    private static final int MAX_CODE_POINTS = 30;

    /**
     * Makes a nickname from a value, which it normalises first. The messages never contain the nickname.
     *
     * @throws IllegalArgumentException if the normalised value is not 5 to 30 code points, or holds a character that is
     *     not allowed
     */
    public Nickname {
        value = TextNormaliser.normalise(value);
        int codePoints = value.codePointCount(0, value.length());

        if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException(
                    String.format("nickname must be 5 to 30 code points, got %s", codePoints));
        }

        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "nickname must hold only letters, marks, digits, spaces, hyphens, apostrophes, full stops"
                            + " and &");
        }
    }
}
