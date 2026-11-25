// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RequestSizeTest extends ApplicationTest {

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
    void acceptsABodyOf4096Bytes() throws IOException, InterruptedException {
        byte[] body = validBodyOf(4_096);

        HttpResponse<String> response = post("application/json", BodyPublishers.ofByteArray(body));

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void refusesABodyOf4097Bytes() throws IOException, InterruptedException {
        byte[] body = validBodyOf(4_097);

        HttpResponse<String> response = post("application/json", BodyPublishers.ofByteArray(body));

        assertTooLarge(response);
    }

    @Test
    void refusesAChunkedBodyOf5000BytesWithNoContentLength() throws IOException, InterruptedException {
        byte[] body = validBodyOf(5_000);

        HttpResponse<String> response =
                post("application/json", BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

        assertTooLarge(response);
    }

    @Test
    void leavesABodyOfAnotherTypeToTheMediaTypeCheck() throws IOException, InterruptedException {
        byte[] body = validBodyOf(5_000);

        HttpResponse<String> response = post("text/plain", BodyPublishers.ofByteArray(body));

        assertThat(response.statusCode()).isEqualTo(415);
    }

    private static void assertTooLarge(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.headers().allValues("Cache-Control"))
                .anySatisfy(value -> assertThat(value).contains("no-store"));
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.propertyNames())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
        assertThat(problem.get("type").asString()).isEqualTo("tag:bitmonkey.co.nz,2026:problem/request-too-large");
        assertThat(problem.get("code").asString()).isEqualTo("request-too-large");
        assertThat(problem.get("status").asInt()).isEqualTo(413);
        assertThat(problem.get("instance").asString()).isEqualTo(PATH);
    }

    private static byte[] validBodyOf(int bytes) {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        byte[] padded = (body + " ".repeat(bytes - body.length())).getBytes(StandardCharsets.UTF_8);
        assertThat(padded).hasSize(bytes);
        return padded;
    }

    private HttpResponse<String> post(String contentType, BodyPublisher body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + PATH))
                .header("Content-Type", contentType)
                .POST(body)
                .build();
        return client.send(request, BodyHandlers.ofString());
    }
}
