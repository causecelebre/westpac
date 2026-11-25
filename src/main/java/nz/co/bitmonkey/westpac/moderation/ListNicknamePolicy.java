// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.moderation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.config.ModerationProperties;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Refuses a nickname when one of its words, or all its words joined, is an entry in a list of offensive words. The list
 * is read once, when the policy is made.
 *
 * <p>The list is UTF-8 text with one entry on each line. {@code #} starts a comment anywhere on a line. Each entry is
 * stripped and lower-cased, and is otherwise used as written. The list holds each entry in the form that
 * {@link NicknameNormaliser} gives a word, with no marks, no substituted digits and no spaces.
 */
@Component
final class ListNicknamePolicy implements NicknamePolicy {

    private static final char COMMENT = '#';

    private final Set<String> entries;

    /**
     * Makes the policy from the list at the configured location. No message gives the text of an entry.
     *
     * @param properties the location of the list
     * @throws IllegalStateException if the list cannot be read or has no entries; the message names the location
     */
    ListNicknamePolicy(ModerationProperties properties) {
        Resource location = properties.listLocation();
        this.entries = entries(lines(location));

        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    String.format("nickname list %s has no entries", location.getDescription()));
        }
    }

    @Override
    public boolean allows(Nickname nickname) {
        List<String> words = NicknameNormaliser.words(nickname.value());

        return words.stream().noneMatch(entries::contains) && !entries.contains(String.join("", words));
    }

    private static Set<String> entries(List<String> lines) {
        return lines.stream()
                .map(line -> withoutComment(line).strip().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> lines(Resource location) {
        try (InputStream in = location.getInputStream()) {
            return IOUtils.readLines(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("nickname list %s cannot be read", location.getDescription()), e);
        }
    }

    private static String withoutComment(String line) {
        int comment = line.indexOf(COMMENT);
        return comment < 0 ? line : line.substring(0, comment);
    }
}
