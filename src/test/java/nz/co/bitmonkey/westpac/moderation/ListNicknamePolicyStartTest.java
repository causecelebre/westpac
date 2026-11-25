// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.config.ModerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class ListNicknamePolicyStartTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Policy.class);

    @Test
    void startsWithTheShippedList() {
        runner.withPropertyValues("westpac.moderation.list-location=classpath:moderation/offensive-nicknames.txt")
                .run(context -> {
                    NicknamePolicy policy = context.getBean(NicknamePolicy.class);

                    assertThat(policy.allows(new Nickname("Rainy Day"))).isTrue();
                    assertThat(policy.allows(new Nickname("Bullsh1t Fund"))).isFalse();
                });
    }

    @Test
    void refusesToStartWithNoFileAtTheLocation() {
        runner.withPropertyValues("westpac.moderation.list-location=classpath:moderation/none.txt")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining(
                                "IllegalStateException: nickname list class path resource [moderation/none.txt]"
                                        + " cannot be read"));
    }

    @Test
    void refusesToStartWithNoLocation() {
        runner.run(context -> assertThat(context)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("westpac.moderation")
                .hasMessageContaining("'listLocation'"));
    }

    @EnableConfigurationProperties(ModerationProperties.class)
    @Import(ListNicknamePolicy.class)
    static class Policy {}
}
