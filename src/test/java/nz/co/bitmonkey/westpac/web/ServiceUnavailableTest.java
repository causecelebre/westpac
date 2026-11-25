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
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ServiceUnavailableTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void answersALockWaitPastTheTransactionTimeoutWith503() throws Exception {
        UUID customer = UUID.randomUUID();
        HttpResponse<String> waited;
        Duration elapsed;

        try (Connection owner = ownerTransaction()) {
            try (PreparedStatement insert = owner.prepareStatement("""
                    insert into savings_account (id, bank, branch, account_base, suffix, customer_id, customer_name,
                        customer_account_seq, created_at)
                    values (?, '03', '1501', '9999999', 999, ?, 'Timeout Test', 1, ?)
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, customer);
                insert.setObject(3, OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC));
                insert.executeUpdate();
            }

            Instant start = Instant.now();
            waited = client.send(createRequest(customer), BodyHandlers.ofString());
            elapsed = Duration.between(start, Instant.now());
            owner.rollback();
        }

        assertThat(waited.statusCode()).isEqualTo(503);
        assertThat(elapsed).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(6));
        assertThat(waited.headers().firstValue("Retry-After")).hasValue("5");
        assertThat(waited.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(waited.headers().allValues("Cache-Control"))
                .anySatisfy(value -> assertThat(value).contains("no-store"));
        JsonNode problem = json.readTree(waited.body());
        assertThat(problem.get("type").asString()).isEqualTo("tag:bitmonkey.co.nz,2026:problem/service-unavailable");
        assertThat(problem.get("code").asString()).isEqualTo("service-unavailable");
        assertThat(problem.get("status").asInt()).isEqualTo(503);
        assertThat(problem.get("title").asString()).isNotBlank();
        assertThat(problem.get("detail").asString()).isNotBlank();
        assertThat(problem.get("instance").asString()).isEqualTo(PATH);

        assertThat(client.send(createRequest(customer), BodyHandlers.ofString()).statusCode())
                .isEqualTo(201);
    }

    private HttpRequest createRequest(UUID customer) {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        return HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
    }
}
