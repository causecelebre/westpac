// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ConnectionLimitsTest extends ApplicationTest {

    @Autowired
    private JdbcClient jdbc;

    @ParameterizedTest
    @CsvSource({"statement_timeout, 3s", "lock_timeout, 2s"})
    void theApplicationsConnectionsCarryTheServerTimeouts(String setting, String value) {
        assertThat(jdbc.sql("show " + setting).query(String.class).single()).isEqualTo(value);
    }
}
