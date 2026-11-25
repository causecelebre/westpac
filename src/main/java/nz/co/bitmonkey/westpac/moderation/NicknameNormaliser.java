// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.moderation;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Puts a nickname into the form that moderation compares with the list entries. Case, diacritics and common digit
 * substitutions do not count, and the nickname is cut into words.
 */
final class NicknameNormaliser {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private NicknameNormaliser() {}

    /**
     * Gives the words of a text, in order, so that different spellings of one word compare equal.
     *
     * <ol>
     *   <li>NFKD splits each accented letter into its base letter and an accent, and the accents are removed: {@code é}
     *       becomes {@code e}. Now café is cafe.
     *   <li>The text is made lower case: {@code BAD} becomes {@code bad}.
     *   <li>Each look-alike digit becomes its letter: {@code 0} to {@code o}, {@code 1} to {@code i}, {@code 3} to
     *       {@code e}, {@code 4} to {@code a}, {@code 5} to {@code s} and {@code 7} to {@code t}.
     *   <li>Cuts it into words. Anything that is not a letter or a digit is a break, such as a space, {@code -},
     *       {@code _} or {@code .}. Empty pieces are dropped.
     * </ol>
     *
     * <p>For example, {@code B4D-Wörd} gives {@code bad} and {@code word}.
     *
     * @param text the nickname
     * @return the words, none of them empty; no words if the nickname has no letter or digit
     */
    static List<String> words(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        String unmarked = MARKS.matcher(decomposed).replaceAll("");
        String substituted = substitute(unmarked.toLowerCase(Locale.ROOT));

        return Arrays.stream(SEPARATORS.split(substituted))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String substitute(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().map(NicknameNormaliser::letterFor).forEach(result::appendCodePoint);

        return result.toString();
    }

    private static int letterFor(int codePoint) {
        return switch (codePoint) {
            case '0' -> 'o';
            case '1' -> 'i';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '7' -> 't';
            default -> codePoint;
        };
    }
}
