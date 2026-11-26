// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PostgresDownTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final Duration IDLE = Duration.ofSeconds(1);
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void answersEachRequestWithPostgresPausedAndRecoversWithNoRestart() throws Exception {
        String cached = idOf(create("0123", null));
        assertThat(get(cached).statusCode()).isEqualTo(200);
        String uncached = idOf(create("0123", null));
        Thread.sleep(IDLE.toMillis());

        withPostgresPaused(() -> {
            long start = System.nanoTime();
            HttpResponse<String> created = create("0123", null);
            Duration took = Duration.ofNanos(System.nanoTime() - start);

            assertUnavailable(created);
            assertThat(took).isLessThan(Duration.ofSeconds(3));
            assertThat(get(cached).statusCode()).isEqualTo(200);
            assertUnavailable(get(uncached));
            assertCode(create("1000", null), 422, "branch-not-allowed");
            assertCode(create("0123", "Bullsh1t Fund"), 422, "nickname-not-allowed");
            return null;
        });

        assertThat(create("0123", null).statusCode()).isEqualTo(201);
    }

    @Test
    void answersACreateSentAtOnceAfterAnotherWith503InUnderSevenSeconds() throws Exception {
        assertThat(create("0123", null).statusCode()).isEqualTo(201);

        withPostgresPaused(() -> {
            long start = System.nanoTime();
            HttpResponse<String> created = create("0123", null);
            Duration took = Duration.ofNanos(System.nanoTime() - start);

            assertUnavailable(created);
            assertThat(took).isLessThan(Duration.ofSeconds(7));
            return null;
        });

        assertThat(create("0123", null).statusCode()).isEqualTo(201);
    }

    private static void assertUnavailable(HttpResponse<String> response) {
        assertCode(response, 503, "service-unavailable");
        assertThat(response.headers().firstValue("Retry-After")).hasValue("5");
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo(code);
    }

    private static String idOf(HttpResponse<String> created) {
        assertThat(created.statusCode()).isEqualTo(201);

        return json.readTree(created.body()).get("id").asString();
    }

    private HttpResponse<String> create(String branch, @Nullable String nickname)
            throws IOException, InterruptedException {
        ObjectNode body = json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", branch)
                .put("customerName", "Jane Citizen");
        if (nickname != null) {
            body.put("nickname", nickname);
        }
        HttpRequest request = HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        return client.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String id) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(PATH + "/" + id)).build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
