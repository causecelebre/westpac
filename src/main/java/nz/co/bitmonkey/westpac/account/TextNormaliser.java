// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Puts a name into its stored form: NFC, then leading and trailing whitespace removed, then each run of spaces (U+0020)
 * made one space. It does not fold case, diacritics or look-alike characters, which is the work of the moderation
 * normaliser.
 */
public final class TextNormaliser {

    private static final Pattern SPACES = Pattern.compile(" +");

    private TextNormaliser() {}

    /**
     * Gives the stored form of a name. A value already in that form comes back unchanged.
     *
     * @param value the name as given
     * @return the name in NFC, stripped, with single spaces
     */
    public static String normalise(String value) {
        String composed = Normalizer.normalize(value, Normalizer.Form.NFC);

        return SPACES.matcher(composed.strip()).replaceAll(" ");
    }
}
