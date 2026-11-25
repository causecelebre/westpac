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
import java.util.UUID;
import java.util.stream.Stream;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RequestRulesTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @Autowired
    private AccountRepository repository;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    static Stream<Arguments> unreadableBodies() {
        String fields =
                "\"customerId\":\"" + UUID.randomUUID() + "\",\"branch\":\"0123\",\"customerName\":\"Jane Citizen\"";
        return Stream.of(
                Arguments.of("misspelt property", "{" + fields + ",\"nickName\":\"Rainy Day Fund\"}"),
                Arguments.of("id", "{" + fields + ",\"id\":\"01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00\"}"),
                Arguments.of("account number", "{" + fields + ",\"accountNumber\":\"03-0123-0000019-000\"}"),
                Arguments.of("duplicate key", "{" + fields + ",\"customerName\":\"John Citizen\"}"),
                Arguments.of("array", "[]"),
                Arguments.of("string", "\"Jane Citizen\""),
                Arguments.of("truncated", "{" + fields),
                Arguments.of("empty", ""),
                Arguments.of(
                        "customer id not a UUID",
                        "{\"customerId\":\"abc\",\"branch\":\"0123\",\"customerName\":\"Jane\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableBodies")
    void answersAnUnreadableBodyWithMalformedRequest(String kind, String body)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(post(body, "application/json"));

        assertProblem(response, 400, "malformed-request", PATH);
    }

    @Test
    void answersAPathIdThatIsNotAUuidWithMalformedRequest() throws IOException, InterruptedException {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(uri(PATH + "/abc")).build());

        assertProblem(response, 400, "malformed-request", PATH + "/abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/foo+json"})
    void answersABodyThatIsNotApplicationJsonWith415(String contentType) throws IOException, InterruptedException {
        HttpResponse<String> response = send(post(validBody(), contentType));

        assertProblem(response, 415, "unsupported-media-type", PATH);
    }

    @Test
    void answersAnAcceptWithNoJsonTypeWith406AndNoBodyAndOpensNoAccount() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .header("Accept", "text/plain")
                .POST(BodyPublishers.ofString(validBody(customer)))
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).isEmpty();
        assertThat(repository.accountsOf(new CustomerId(customer)).numbers()).isEmpty();
    }

    @Test
    void answersAnUnplannedFaultWithInternalErrorAndLogsItInFull(CapturedOutput output) throws Exception {
        HttpResponse<String> response = withAccountNumbersRunOut(() -> send(post(validBody(), "application/json")));

        assertProblem(response, 500, "internal-error", PATH);
        assertThat(output.getOut())
                .contains("ERROR")
                .contains("Unplanned fault, answered with 500 internal-error")
                .contains("account_number_seq")
                .contains("\tat org.springframework.jdbc.");
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code, String instance) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body())
                .doesNotContain("Exception")
                .doesNotContain("at nz.")
                .doesNotContain("org.")
                .doesNotContainIgnoringCase("SQL")
                .doesNotContainIgnoringCase("select");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("type").asString()).isEqualTo("tag:bitmonkey.co.nz,2026:problem/" + code);
        assertThat(problem.get("code").asString()).isEqualTo(code);
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("title").asString()).isNotBlank();
        assertThat(problem.get("detail").asString()).isNotBlank();
        assertThat(problem.get("instance").asString()).isEqualTo(instance);
    }

    private static String validBody() {
        return validBody(UUID.randomUUID());
    }

    private static String validBody(UUID customer) {
        return json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
    }

    private HttpRequest post(String body, String contentType) {
        return HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", contentType)
                .POST(BodyPublishers.ofString(body))
                .build();
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
