// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class CustomerAccountLimitTest extends ApplicationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void maxEqualsTheUpperBoundOfTheSchemaCheck() {
        String definition = jdbc.sql(
                        "select pg_get_constraintdef(oid) from pg_constraint where conname = 'savings_account_seq_ck'")
                .query(String.class)
                .single();
        Matcher upperBound = Pattern.compile("customer_account_seq <= (\\d+)").matcher(definition);

        assertThat(upperBound.find()).as(definition).isTrue();
        assertThat(CustomerAccountLimit.MAX).isEqualTo(Integer.parseInt(upperBound.group(1)));
    }
}
