// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.AccountIdGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(OutputCaptureExtension.class)
class RedisDownTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final String CUSTOMER_NAME = "Jane Citizen";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @Autowired
    private MeterRegistry registry;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void readsTheDatabaseAndReplacesAnEntryThatDoesNotDeserialise(CapturedOutput output) throws Exception {
        String id = create();
        HttpResponse<String> first = get(id);
        JsonNode entry = json.readTree(redisCli("get", key(id)));
        ObjectNode bad = (ObjectNode) entry.deepCopy();
        ((ObjectNode) bad.get("number").get("branch")).put("value", "12");
        assertThat(redisCli("set", key(id), json.writeValueAsString(bad), "ex", "600"))
                .isEqualTo("OK");
        double gets = errors("get");
        double puts = errors("put");

        HttpResponse<String> read = get(id);

        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(json.readTree(read.body())).isEqualTo(json.readTree(first.body()));
        assertThat(json.readTree(redisCli("get", key(id)))).isEqualTo(entry);
        assertThat(errors("get")).isEqualTo(gets + 1);
        assertThat(errors("put")).isEqualTo(puts);
        assertThat(output.getOut().lines())
                .anyMatch(line -> line.contains(" WARN ") && line.contains("Cache get failed") && line.contains(id));
        assertThat(output.getOut()).doesNotContain(CUSTOMER_NAME);
    }

    @Test
    void createsAndReadsWithRedisPaused() throws Exception {
        double gets = errors("get");
        double puts = errors("put");

        withRedisPaused(() -> {
            String id = create();
            long start = System.nanoTime();
            HttpResponse<String> read = get(id);
            Duration took = Duration.ofNanos(System.nanoTime() - start);

            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(json.readTree(read.body()).get("id").asString()).isEqualTo(id);
            assertThat(took).isLessThan(Duration.ofSeconds(1));
            return null;
        });

        assertThat(errors("get")).isEqualTo(gets + 1);
        assertThat(errors("put")).isEqualTo(puts + 1);
    }

    @Test
    void fillsTheCacheAgainAfterRedisIsUnpaused() throws Exception {
        String id = withRedisPaused(this::create);

        assertThat(redisCli("exists", key(id))).isEqualTo("0");

        HttpResponse<String> read = get(id);

        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(redisCli("exists", key(id))).isEqualTo("1");
    }

    @Test
    void countsNoErrorForAMissWithRedisUp() throws Exception {
        String id = new AccountIdGenerator(Clock.systemUTC()).next().value().toString();
        double gets = errors("get");
        double puts = errors("put");

        HttpResponse<String> read = get(id);

        assertThat(read.statusCode()).isEqualTo(404);
        assertThat(redisCli("exists", key(id))).isEqualTo("0");
        assertThat(errors("get")).isEqualTo(gets);
        assertThat(errors("put")).isEqualTo(puts);
    }

    private double errors(String operation) {
        return registry.get("westpac.cache.errors")
                .tag("operation", operation)
                .counter()
                .count();
    }

    private static String key(String id) {
        return "westpac:v1:account:" + id;
    }

    private String create() throws IOException, InterruptedException {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", "0123")
                .put("customerName", CUSTOMER_NAME)
                .put("nickname", "Rainy Day Fund"));
        HttpRequest request = HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> created = client.send(request, BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);

        return json.readTree(created.body()).get("id").asString();
    }

    private HttpResponse<String> get(String id) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(PATH + "/" + id)).build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
