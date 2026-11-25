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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CreateAndReadTest extends ApplicationTest {

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

    @Test
    void createsAnAccountAndReadsItBack() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();

        HttpResponse<String> created = post(body(customer.toString(), "0123", "Jane Citizen", "Rainy Day Fund"));

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Content-Type")).hasValue("application/json");
        JsonNode account = json.readTree(created.body());
        assertThat(account.propertyNames())
                .containsExactlyInAnyOrder(
                        "id", "accountNumber", "customerId", "customerName", "nickname", "createdAt");
        String id = account.get("id").asString();
        assertThat(UUID.fromString(id).version()).isEqualTo(7);
        assertThat(created.headers().firstValue("Location")).hasValue(PATH + "/" + id);
        assertThat(account.get("accountNumber").asString()).matches("03-0123-\\d{7}-000");
        assertThat(account.get("customerId").asString()).isEqualTo(customer.toString());
        assertThat(account.get("customerName").asString()).isEqualTo("Jane Citizen");
        assertThat(account.get("nickname").asString()).isEqualTo("Rainy Day Fund");
        assertThat(account.get("createdAt").isString()).isTrue();
        assertThat(Instant.parse(account.get("createdAt").asString())).isBeforeOrEqualTo(Instant.now());

        HttpResponse<String> read = get(created.headers().firstValue("Location").orElseThrow());

        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(json.readTree(read.body())).isEqualTo(account);
    }

    @Test
    void givesANullNicknameWhenNoneWasSent() throws IOException, InterruptedException {
        HttpResponse<String> created = post(body(UUID.randomUUID().toString(), "0123", "Jane Citizen", null));

        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode account = json.readTree(created.body());
        assertThat(account.has("nickname")).isTrue();
        assertThat(account.get("nickname").isNull()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "unknown version 7, 01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00",
        "version 4, 7d3f9c2a-5b1e-4f6a-8c0d-2e9b4a7f1c35"
    })
    void answersAnIdWithNoAccountWith404(String kind, String id) throws IOException, InterruptedException {
        HttpResponse<String> response = get(PATH + "/" + id);

        assertProblem(response, 404, "account-not-found", PATH + "/" + id);
    }

    @Test
    void refusesABranchTheBankDoesNotOwnAndWritesNoRow() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();

        HttpResponse<String> response = post(body(customer.toString(), "1000", "Jane Citizen", null));

        assertProblem(response, 422, "branch-not-allowed", PATH);
        assertThat(repository.accountsOf(new CustomerId(customer)).numbers()).isEmpty();
    }

    @Test
    void refusesAnOffensiveNicknameAndWritesNoRow() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();

        HttpResponse<String> response = post(body(customer.toString(), "0123", "Jane Citizen", "Bullsh1t Fund"));

        assertProblem(response, 422, "nickname-not-allowed", PATH);
        assertThat(response.body()).doesNotContainIgnoringCase("bullsh").doesNotContainIgnoringCase("fund");
        assertThat(repository.accountsOf(new CustomerId(customer)).numbers()).isEmpty();
    }

    @Test
    void refusesTheSixthAccount() throws IOException, InterruptedException {
        String customer = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            assertThat(post(body(customer, "0123", "Jane Citizen", null)).statusCode())
                    .isEqualTo(201);
        }

        HttpResponse<String> response = post(body(customer, "0123", "Jane Citizen", null));

        assertProblem(response, 422, "account-limit-reached", PATH);
    }

    @Test
    void sharesOneLimitBetweenUpperAndLowerCaseCustomerIds() throws IOException, InterruptedException {
        String customer = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            String spelling = i % 2 == 0 ? customer : customer.toUpperCase(Locale.ROOT);
            assertThat(post(body(spelling, "0123", "Jane Citizen", null)).statusCode())
                    .isEqualTo(201);
        }

        HttpResponse<String> response = post(body(customer.toUpperCase(Locale.ROOT), "0123", "Jane Citizen", null));

        assertProblem(response, 422, "account-limit-reached", PATH);
    }

    @Test
    void replaysACreateWithTheSameKeyAndOpensOneAccount() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String request = body(customer.toString(), "0123", "Jane Citizen", "Rainy Day Fund");

        HttpResponse<String> first = post(request, key);
        HttpResponse<String> second = post(request, key);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(second.headers().allValues("Idempotent-Replayed")).containsExactly("true");
        assertThat(second.headers().firstValue("Location"))
                .isPresent()
                .isEqualTo(first.headers().firstValue("Location"));
        assertThat(json.readTree(second.body())).isEqualTo(json.readTree(first.body()));
        assertThat(repository.accountsOf(new CustomerId(customer)).numbers()).hasSize(1);
    }

    @Test
    void replaysALowerCaseKeySentInUpperCase() throws IOException, InterruptedException {
        String key = UUID.randomUUID().toString();
        String request = body(UUID.randomUUID().toString(), "0123", "Jane Citizen", null);

        HttpResponse<String> first = post(request, key);
        HttpResponse<String> second = post(request, key.toUpperCase(Locale.ROOT));

        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(idOf(second)).isEqualTo(idOf(first));
    }

    @Test
    void refusesTheSameKeyWithAnotherNicknameAndWritesNoRow() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        assertThat(post(body(customer.toString(), "0123", "Jane Citizen", "Rainy Day Fund"), key)
                        .statusCode())
                .isEqualTo(201);

        HttpResponse<String> response = post(body(customer.toString(), "0123", "Jane Citizen", "Holiday Fund"), key);

        assertProblem(response, 422, "idempotency-key-reused", PATH);
        assertThat(repository.accountsOf(new CustomerId(customer)).numbers()).hasSize(1);
    }

    @Test
    void replaysABodyThatNormalisesToTheFirstBody() throws IOException, InterruptedException {
        String customer = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        HttpResponse<String> first = post(body(customer, "0123", "Jane Citizen", null), key);
        HttpResponse<String> second = post(body(customer, "0123", "Jane  Citizen", null), key);

        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(idOf(second)).isEqualTo(idOf(first));
    }

    @Test
    void opensAnAccountForEachCustomerThatSendsTheSameKey() throws IOException, InterruptedException {
        UUID jane = UUID.randomUUID();
        UUID john = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        HttpResponse<String> forJane = post(body(jane.toString(), "0123", "Jane Citizen", null), key);
        HttpResponse<String> forJohn = post(body(john.toString(), "0123", "Jane Citizen", null), key);

        assertThat(forJane.statusCode()).isEqualTo(201);
        assertThat(forJohn.statusCode()).isEqualTo(201);
        assertThat(forJohn.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(idOf(forJohn)).isNotEqualTo(idOf(forJane));
        assertThat(repository.accountsOf(new CustomerId(jane)).numbers()).hasSize(1);
        assertThat(repository.accountsOf(new CustomerId(john)).numbers()).hasSize(1);
    }

    @Test
    void replaysTheFifthAccountWhenTheCustomerIsAtTheLimit() throws IOException, InterruptedException {
        UUID customer = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            assertThat(post(body(customer.toString(), "0123", "Jane Citizen", null))
                            .statusCode())
                    .isEqualTo(201);
        }
        String key = UUID.randomUUID().toString();
        String fifth = body(customer.toString(), "0123", "Jane Citizen", "Rainy Day Fund");
        HttpResponse<String> first = post(fifth, key);
        assertThat(first.statusCode()).isEqualTo(201);

        HttpResponse<String> replay = post(fifth, key);

        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(idOf(replay)).isEqualTo(idOf(first));
        assertThat(repository.accountsOf(new CustomerId(customer)).numbers()).hasSize(5);
    }

    @Test
    void answersAnUnplannedFaultWith500() throws Exception {
        HttpResponse<String> response =
                withAccountNumbersRunOut(() -> post(body(UUID.randomUUID().toString(), "0123", "Jane Citizen", null)));

        assertThat(response.statusCode()).isEqualTo(500);
    }

    @ParameterizedTest
    @CsvSource({
        "GET, /api/v1/savings-accounts",
        "PUT, /api/v1/savings-accounts/01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00",
        "DELETE, /api/v1/savings-accounts/01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00",
        "POST, /api/v1/savings-accounts/01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00",
        "GET, /api/v1/other",
        "GET, /api/v2/savings-accounts/01936b2e-7c4a-7f3d-9a51-2c8e4b6d1f00"
    })
    void refusesOtherMethodsAndPathsWith401(String method, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .method(method, BodyPublishers.ofString("{}"))
                .build();

        assertThat(client.send(request, BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    private void assertProblem(HttpResponse<String> response, int status, String code, String instance) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("type").asString()).isEqualTo("tag:bitmonkey.co.nz,2026:problem/" + code);
        assertThat(problem.get("code").asString()).isEqualTo(code);
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("title").asString()).isNotBlank();
        assertThat(problem.get("detail").asString()).isNotBlank();
        assertThat(problem.get("instance").asString()).isEqualTo(instance);
        assertNoStore(response);
    }

    private static void assertNoStore(HttpResponse<String> response) {
        assertThat(response.headers().allValues("Cache-Control"))
                .anySatisfy(value -> assertThat(value).contains("no-store"));
    }

    private static String body(String customerId, String branch, String customerName, @Nullable String nickname) {
        ObjectNode body = json.createObjectNode()
                .put("customerId", customerId)
                .put("branch", branch)
                .put("customerName", customerName);
        if (nickname != null) {
            body.put("nickname", nickname);
        }
        return json.writeValueAsString(body);
    }

    private static String idOf(HttpResponse<String> response) {
        return json.readTree(response.body()).get("id").asString();
    }

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(PATH)), body);
    }

    private HttpResponse<String> post(String body, String idempotencyKey) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(PATH)).header("Idempotency-Key", idempotencyKey), body);
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder, String body)
            throws IOException, InterruptedException {
        HttpRequest request = builder.header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        assertNoStore(response);
        return response;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpResponse<String> response =
                client.send(HttpRequest.newBuilder(uri(path)).build(), BodyHandlers.ofString());
        assertNoStore(response);
        return response;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
