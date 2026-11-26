// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PoolFullTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final int POOL_SIZE = 10;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void answersACreateThatFindsNoFreeConnectionWith503AfterTheConnectionTimeout() throws Exception {
        HttpResponse<String> refused;
        Duration elapsed;

        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < POOL_SIZE; i++) {
                held.add(dataSource.getConnection());
            }

            Instant start = Instant.now();
            refused = client.send(createRequest(), BodyHandlers.ofString());
            elapsed = Duration.between(start, Instant.now());
        } finally {
            closeAll(held);
        }

        assertThat(refused.statusCode()).isEqualTo(503);
        assertThat(elapsed).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(3));
        assertThat(refused.headers().firstValue("Retry-After")).hasValue("5");
        JsonNode problem = json.readTree(refused.body());
        assertThat(problem.get("code").asString()).isEqualTo("service-unavailable");

        assertThat(client.send(createRequest(), BodyHandlers.ofString()).statusCode())
                .isEqualTo(201);
    }

    private static void closeAll(List<Connection> connections) throws SQLException {
        for (Connection connection : connections) {
            connection.close();
        }
    }

    private HttpRequest createRequest() {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        return HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
    }
}
