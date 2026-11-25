// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

class SchemaBackstopsTest extends ApplicationTest {

    private static final String FINGERPRINT = "0".repeat(64);
    private static final String OUTSIDE_THE_BMP = Character.toString(0x1f600);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    static Stream<Arguments> checkViolations() {
        return Stream.of(
                Arguments.of("savings_account_seq_ck", "customer_account_seq", 0),
                Arguments.of("savings_account_seq_ck", "customer_account_seq", 6),
                Arguments.of("savings_account_nickname_ck", "nickname", "a".repeat(4)),
                Arguments.of("savings_account_nickname_ck", "nickname", "a".repeat(31)),
                Arguments.of("savings_account_bank_ck", "bank", "ab"),
                Arguments.of("savings_account_branch_ck", "branch", "12a4"),
                Arguments.of("savings_account_base_ck", "account_base", "123456a"),
                Arguments.of("savings_account_suffix_ck", "suffix", -1),
                Arguments.of("savings_account_suffix_ck", "suffix", 1000),
                Arguments.of("savings_account_idempotency_ck", "idempotency_key", UUID.randomUUID()),
                Arguments.of("savings_account_idempotency_ck", "request_fingerprint", FINGERPRINT));
    }

    @ParameterizedTest
    @MethodSource("checkViolations")
    void eachCheckConstraintRefusesItsRow(String constraint, String column, Object value) {
        Map<String, @Nullable Object> row = validRow();
        row.put(column, value);

        assertRefused(() -> insert(row), "23514", constraint);
    }

    static Stream<Arguments> uniqueViolations() {
        return Stream.of(
                Arguments.of("savings_account_number_uk", new String[] {"bank", "branch", "account_base", "suffix"}),
                Arguments.of("savings_account_customer_uk", new String[] {"customer_id", "customer_account_seq"}),
                Arguments.of(
                        "savings_account_idempotent_uk",
                        new String[] {"customer_id", "idempotency_key", "request_fingerprint"}));
    }

    @ParameterizedTest
    @MethodSource("uniqueViolations")
    void eachUniqueConstraintRefusesASecondRow(String constraint, String[] sharedColumns) {
        Map<String, @Nullable Object> first = validRow();
        first.put("idempotency_key", UUID.randomUUID());
        first.put("request_fingerprint", FINGERPRINT);
        Map<String, @Nullable Object> second = validRow();
        second.put("customer_account_seq", 2);
        for (String column : sharedColumns) {
            second.put(column, first.get(column));
        }

        assertRefused(
                () -> {
                    insert(first);
                    insert(second);
                },
                "23505",
                constraint);
    }

    @Test
    void storesA30CodePointNicknameWithACharacterOutsideTheBmp() {
        String nickname = "a".repeat(29) + OUTSIDE_THE_BMP;
        Map<String, @Nullable Object> row = validRow();
        row.put("nickname", nickname);

        String stored = transactions.execute(status -> {
            status.setRollbackOnly();
            insert(row);
            return jdbc.sql("select nickname from savings_account where id = :id")
                    .param("id", row.get("id"))
                    .query(String.class)
                    .single();
        });

        assertThat(stored).isEqualTo(nickname);
    }

    @Test
    void refusesA31CodePointNicknameWithACharacterOutsideTheBmp() {
        Map<String, @Nullable Object> row = validRow();
        row.put("nickname", "a".repeat(30) + OUTSIDE_THE_BMP);

        assertRefused(() -> insert(row), "23514", "savings_account_nickname_ck");
    }

    private void assertRefused(Runnable work, String sqlState, String constraint) {
        Throwable thrown = catchThrowable(() -> transactions.executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.run();
        }));

        assertThat(NestedExceptionUtils.getRootCause(thrown)).isInstanceOfSatisfying(SQLException.class, e -> {
            assertThat(e.getSQLState()).isEqualTo(sqlState);
            assertThat(e.getMessage()).contains("\"" + constraint + "\"");
        });
    }

    private void insert(Map<String, @Nullable Object> row) {
        jdbc.sql("""
                        insert into savings_account (id, bank, branch, account_base, suffix, customer_id,
                            customer_name, customer_account_seq, nickname, idempotency_key, request_fingerprint,
                            created_at)
                        values (:id, :bank, :branch, :account_base, :suffix, :customer_id, :customer_name,
                            :customer_account_seq, :nickname, :idempotency_key, :request_fingerprint, :created_at)
                        """).params(row).update();
    }

    private static Map<String, @Nullable Object> validRow() {
        Map<String, @Nullable Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("bank", "03");
        row.put("branch", "0123");
        row.put(
                "account_base",
                String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000)));
        row.put("suffix", 999);
        row.put("customer_id", UUID.randomUUID());
        row.put("customer_name", "Backstop Test");
        row.put("customer_account_seq", 1);
        row.put("nickname", null);
        row.put("idempotency_key", null);
        row.put("request_fingerprint", null);
        row.put("created_at", OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC));
        return row;
    }
}
