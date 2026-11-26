// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RequestIdTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final String HEADER = "X-Request-Id";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void echoesAValidIdInTheHeaderAndInAProblemBody() throws Exception {
        String id = "client-" + UUID.randomUUID();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri(PATH + "/" + UUID.randomUUID()))
                        .header(HEADER, id)
                        .build(),
                BodyHandlers.ofString());

        assertThat(response.headers().firstValue(HEADER)).hasValue(id);
        assertThat(json.readTree(response.body()).get("requestId").asString()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"a1234567890123456789012345678901234567890123456789012345678901234", "has space", "semi;colon"})
    void replacesAnIdOfTheWrongFormWithANewUuid(String sent) throws Exception {
        HttpResponse<String> response = create(sent);

        String given = response.headers().firstValue(HEADER).orElseThrow();
        assertThat(given).isNotEqualTo(sent);
        assertThat(UUID.fromString(given).toString()).isEqualTo(given);
    }

    @Test
    void givesANewUuidWhenTheClientSendsNoId() throws Exception {
        String given = create(null).headers().firstValue(HEADER).orElseThrow();

        assertThat(UUID.fromString(given).toString()).isEqualTo(given);
    }

    @Test
    void putsTheIdOnARefusedRequest() throws Exception {
        HttpResponse<String> refused = client.send(
                HttpRequest.newBuilder(uri("/x")).header(HEADER, "refused-1").build(), BodyHandlers.ofString());

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(refused.headers().firstValue(HEADER)).hasValue("refused-1");
    }

    @Test
    void putsTheIdOfEachRequestInItsAccountOpenedLine(CapturedOutput output) throws Exception {
        String sent = "opened-" + UUID.randomUUID();
        String first = idOf(create(sent));
        HttpResponse<String> second = create(null);
        String secondId = second.headers().firstValue(HEADER).orElseThrow();

        assertThat(openedLine(output, first)).contains("[" + sent + "]");
        assertThat(openedLine(output, idOf(second)))
                .contains("[" + secondId + "]")
                .doesNotContain(sent);
    }

    private static String openedLine(CapturedOutput output, String accountId) {
        return output.getOut()
                .lines()
                .filter(line -> line.contains("Account opened") && line.contains(accountId))
                .findFirst()
                .orElseThrow();
    }

    private static String idOf(HttpResponse<String> created) {
        assertThat(created.statusCode()).isEqualTo(201);

        return json.readTree(created.body()).get("id").asString();
    }

    private HttpResponse<String> create(@Nullable String requestId) throws IOException, InterruptedException {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body));
        if (requestId != null) {
            request.header(HEADER, requestId);
        }

        return client.send(request.build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
