// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

class PrivilegesTest extends ApplicationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void theApplicationConnectsAsWestpacApp() {
        String user = rolledBack(
                db -> db.sql("select current_user").query(String.class).single());

        assertThat(user).isEqualTo("westpac_app");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "update savings_account set nickname = null",
                "delete from savings_account",
                "drop table savings_account",
                "create table privilege_probe (id integer)"
            })
    void changingRowsOrTheSchemaIsRefused(String statement) {
        Throwable thrown =
                catchThrowable(() -> rolledBack(db -> db.sql(statement).update()));

        assertThat(NestedExceptionUtils.getRootCause(thrown))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
    }

    private <T> T rolledBack(Function<JdbcClient, T> work) {
        return transactions.execute(status -> {
            status.setRollbackOnly();
            return work.apply(jdbc);
        });
    }
}
