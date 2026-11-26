// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

class ManagementEndpointsTest extends ApplicationTest {

    private static final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void readinessIsUpOnTheManagementPort() throws IOException, InterruptedException {
        HttpResponse<String> response = get(managementPort(), "/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonMapper.shared().readTree(response.body()).get("status").asString())
                .isEqualTo("UP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/info"})
    void healthAndInfoArePermitted(String path) throws IOException, InterruptedException {
        assertThat(get(managementPort(), path).statusCode()).isEqualTo(200);
    }

    @Test
    void prometheusGivesTheServiceCountersAndThePoolMetrics() throws IOException, InterruptedException {
        HttpResponse<String> response = get(managementPort(), "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("westpac_cache_errors_total").contains("hikaricp_connections");
    }

    @ParameterizedTest
    @CsvSource({"management, /actuator", "management, /actuator/env", "api, /x"})
    void everythingElseIsRefusedWith401(String port, String path) throws IOException, InterruptedException {
        int number = port.equals("management") ? managementPort() : apiPort();

        assertThat(get(number, path).statusCode()).isEqualTo(401);
    }

    private int apiPort() {
        return environment.getRequiredProperty("local.server.port", Integer.class);
    }

    private int managementPort() {
        return environment.getRequiredProperty("local.management.port", Integer.class);
    }

    private static HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
