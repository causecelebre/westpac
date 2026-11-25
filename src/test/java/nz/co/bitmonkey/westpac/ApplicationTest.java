// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
