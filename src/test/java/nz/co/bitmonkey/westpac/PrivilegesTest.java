// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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

    @Test
    void selectSucceeds() {
        Long rows = rolledBack(db ->
                db.sql("select count(*) from savings_account").query(Long.class).single());

        assertThat(rows).isNotNegative();
    }

    @Test
    void insertSucceeds() {
        String base = String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
        Integer inserted = rolledBack(db -> db.sql("""
                        insert into savings_account (id, bank, branch, account_base, suffix, customer_id,
                            customer_name, customer_account_seq, created_at)
                        values (:id, '03', '0123', :base, 999, :customer, 'Privilege Test', 1, :created)
                        """)
                .param("id", UUID.randomUUID())
                .param("base", base)
                .param("customer", UUID.randomUUID())
                .param("created", OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC))
                .update());

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void nextvalOnTheAccountNumberSequenceSucceeds() {
        Long next = rolledBack(db ->
                db.sql("select nextval('account_number_seq')").query(Long.class).single());

        assertThat(next).isPositive();
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
