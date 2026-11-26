// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReadinessTest extends ApplicationTest {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void isNotReadyButStillLiveWhilePostgresIsPaused() throws Exception {
        withPostgresPaused(() -> {
            assertStatus("/actuator/health/readiness", 503, "DOWN");
            assertStatus("/actuator/health/liveness", 200, "UP");
            return null;
        });

        assertStatus("/actuator/health/readiness", 200, "UP");
    }

    @Test
    void staysReadyWhileOnlyRedisIsPaused() throws Exception {
        withRedisPaused(() -> {
            assertStatus("/actuator/health/readiness", 200, "UP");
            return null;
        });
    }

    @Test
    void theFullReportListsRedisAndGivesNoDetails() throws Exception {
        JsonNode report = json.readTree(get("/actuator/health").body());

        assertThat(report.get("components").get("redis").get("status").asString())
                .isEqualTo("UP");
        assertThat(report.findValues("details")).isEmpty();
    }

    private void assertStatus(String path, int code, String status) throws IOException, InterruptedException {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode()).isEqualTo(code);
        assertThat(json.readTree(response.body()).get("status").asString()).isEqualTo(status);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        int port = environment.getRequiredProperty("local.management.port", Integer.class);
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .build(),
                BodyHandlers.ofString());
    }
}
