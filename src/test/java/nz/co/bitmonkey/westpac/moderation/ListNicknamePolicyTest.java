// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.charset.StandardCharsets;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.config.ModerationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

class ListNicknamePolicyTest {

    private static final ListNicknamePolicy POLICY = policy("badword\nstinker\n");

    private static ListNicknamePolicy policy(String list) {
        ByteArrayResource location = new ByteArrayResource(list.getBytes(StandardCharsets.UTF_8), "test list");
        return new ListNicknamePolicy(new ModerationProperties(location));
    }

    private static boolean allows(NicknamePolicy policy, String nickname) {
        return policy.allows(new Nickname(nickname));
    }

    @Test
    void refusesAnExactEntry() {
        assertThat(allows(POLICY, "badword")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BADWORD", "BadWord"})
    void refusesAnEntryInAnyCase(String nickname) {
        assertThat(allows(POLICY, nickname)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bádwörd", "Bádwörd Fund"})
    void refusesAnEntryWithDiacritics(String nickname) {
        assertThat(allows(POLICY, nickname)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"b4dword", "badw0rd", "st1nker", "stink3r", "5tinker", "s7inker"})
    void refusesAnEntryWithADigitSubstitution(String nickname) {
        assertThat(allows(POLICY, nickname)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Badword Fund", "My stinker"})
    void refusesANicknameWithOneListedWord(String nickname) {
        assertThat(allows(POLICY, nickname)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bad Word", "bad-word", "Stin Ker"})
    void refusesANicknameWhoseWordsJoinedAreListed(String nickname) {
        assertThat(allows(POLICY, nickname)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Badwords Fund", "Stinkers", "My Bad Word Fund", "Rainy Day", "-.-.-"})
    void allowsANicknameWithNoListedWord(String nickname) {
        assertThat(allows(POLICY, nickname)).isTrue();
    }

    @Test
    void lowerCasesAnEntry() {
        assertThat(allows(policy("BadWord\n"), "badword")).isFalse();
    }

    @Test
    void addsNoEntryForACommentOrABlankLine() {
        ListNicknamePolicy policy = policy("# rainy\n\n   \nstinker # rainy day\n#stinker2\n");

        assertThat(allows(policy, "stinker")).isFalse();
        assertThat(allows(policy, "rainy")).isTrue();
        assertThat(allows(policy, "Rainy Day")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# one\n\n   \n# two\n"})
    void refusesAListWithNoEntries(String list) {
        assertThatIllegalStateException()
                .isThrownBy(() -> policy(list))
                .withMessage("nickname list Byte array resource [test list] has no entries");
    }
}
