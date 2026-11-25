// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0", "management.server.port=0"})
public abstract class ApplicationTest {

    private static final String INIT_SCRIPT = "docker/postgres/init/01-roles-and-schema.sql";
    private static final String OWNER_PASSWORD = "test-owner-password";
    private static final String APP_PASSWORD = "test-app-password";
    private static final PostgreSQLContainer POSTGRES = startPostgres();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registry.add("DB_APP_PASSWORD", () -> APP_PASSWORD);
        registry.add("DB_OWNER_PASSWORD", () -> OWNER_PASSWORD);
    }

    protected static JdbcClient asOwner() {
        return JdbcClient.create(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "westpac_owner", OWNER_PASSWORD));
    }

    protected static JdbcClient asSuperuser() {
        return JdbcClient.create(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    protected static Connection ownerTransaction() throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "westpac_owner", OWNER_PASSWORD);
        connection.setAutoCommit(false);

        return connection;
    }

    protected static void awaitAppSessionsWaitingOnALock(int sessions) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(4));
        while (Instant.now().isBefore(deadline)) {
            int waiting = asSuperuser().sql("""
                            select count(*) from pg_stat_activity
                            where usename = 'westpac_app' and wait_event_type = 'Lock'
                            """).query(Integer.class).single();
            if (waiting == sessions) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the app sessions waiting on a lock did not reach " + sessions);
    }

    protected static <T> T withAccountNumbersRunOut(Callable<T> action) throws Exception {
        JdbcClient owner = asOwner();

        owner.sql("select nextval('account_number_seq')").query(Long.class).single();
        long last = owner.sql("select nextval('account_number_seq')")
                .query(Long.class)
                .single();
        owner.sql("alter sequence account_number_seq maxvalue " + last).update();

        try {
            return action.call();
        } finally {
            owner.sql("alter sequence account_number_seq maxvalue 999999").update();
        }
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer startPostgres() {
        PostgreSQLContainer container = new PostgreSQLContainer(composePostgresImage())
                .withDatabaseName("westpac")
                .withEnv("DB_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_SCRIPT), "/docker-entrypoint-initdb.d/01-roles-and-schema.sql");
        container.start();

        return container;
    }

    private static DockerImageName composePostgresImage() {
        try {
            Matcher image = Pattern.compile("^\\s+image:\\s+(postgres:\\S+)$", Pattern.MULTILINE)
                    .matcher(Files.readString(Path.of("compose.yaml")));
            if (!image.find()) {
                throw new IllegalStateException("compose.yaml names no postgres image");
            }

            return DockerImageName.parse(image.group(1)).asCompatibleSubstituteFor("postgres");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
