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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class FieldRulesTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final String ABSENT = "<absent>";
    private static final String NON_BMP_LETTER = "𠀀";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    static Stream<Arguments> brokenRules() {
        return Stream.of(
                Arguments.of("customerId", ABSENT, "required"),
                Arguments.of("customerId", null, "required"),
                Arguments.of("branch", ABSENT, "required"),
                Arguments.of("branch", null, "required"),
                Arguments.of("branch", "", "blank"),
                Arguments.of("branch", "123", "length"),
                Arguments.of("branch", "12345", "length"),
                Arguments.of("branch", "12a4", "characters"),
                Arguments.of("branch", "١٢٣٤", "characters"),
                Arguments.of("customerName", ABSENT, "required"),
                Arguments.of("customerName", null, "required"),
                Arguments.of("customerName", "", "blank"),
                Arguments.of("customerName", "   ", "blank"),
                Arguments.of("customerName", "a".repeat(101), "length"),
                Arguments.of("customerName", "Jane 2", "characters"),
                Arguments.of("customerName", "- . '", "characters"),
                Arguments.of("customerName", "Jane 🙂", "characters"),
                Arguments.of("customerName", "Ja" + Character.toString(0x200D) + "ne", "characters"),
                Arguments.of("customerName", "Ja" + Character.toString(0x202E) + "ne", "characters"),
                Arguments.of("customerName", "Ja" + Character.toString(0x0009) + "ne", "characters"),
                Arguments.of("nickname", "", "blank"),
                Arguments.of("nickname", "     ", "blank"),
                Arguments.of("nickname", "Fund", "length"),
                Arguments.of("nickname", "a".repeat(31), "length"),
                Arguments.of("nickname", "Fund!", "characters"),
                Arguments.of("nickname", "Fund 🙂", "characters"));
    }

    @ParameterizedTest
    @MethodSource("brokenRules")
    void reportsTheFieldAndItsCode(String field, @Nullable String value, String code)
            throws IOException, InterruptedException {
        HttpResponse<String> response = post(valid().with(field, value));

        JsonNode problem = assertValidationFailed(response);
        assertThat(problem.get("detail").asString()).isEqualTo("1 field is not valid");
        assertThat(errors(problem)).containsExactly(field + ":" + code);
    }

    @Test
    void tellsTheClientToLeaveOutABlankNickname() throws IOException, InterruptedException {
        HttpResponse<String> response = post(valid().with("nickname", "     "));

        JsonNode error = assertValidationFailed(response).get("errors").get(0);
        assertThat(error.get("message").asString())
                .isEqualTo("must not be empty or only spaces; leave it out for no nickname");
    }

    @Test
    void reportsEveryFailingFieldAtOnce() throws IOException, InterruptedException {
        HttpResponse<String> response =
                post(valid().with("branch", "12a4").with("customerName", "").with("nickname", "Fund"));

        JsonNode problem = assertValidationFailed(response);
        assertThat(problem.get("detail").asString()).isEqualTo("3 fields are not valid");
        assertThat(errors(problem)).containsExactly("branch:characters", "customerName:blank", "nickname:length");
        assertThat(problem.get("errors").get(0).get("message").asString()).isNotBlank();
    }

    @Test
    void storesTheNameNormalised() throws IOException, InterruptedException {
        HttpResponse<String> response = post(valid().with("customerName", "  Jane   Citizen "));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json.readTree(response.body()).get("customerName").asString())
                .isEqualTo("Jane Citizen");
    }

    @Test
    void acceptsANameOf100CodePointsAfterTrimming() throws IOException, InterruptedException {
        HttpResponse<String> response = post(valid().with("customerName", "  " + "a".repeat(100)));

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void countsANicknameInCodePoints() throws IOException, InterruptedException {
        String nickname = NON_BMP_LETTER + "a".repeat(29);

        HttpResponse<String> created = post(valid().with("nickname", nickname));

        assertThat(created.statusCode()).isEqualTo(201);
        HttpResponse<String> read = get(created.headers().firstValue("Location").orElseThrow());
        assertThat(json.readTree(read.body()).get("nickname").asString()).isEqualTo(nickname);
    }

    @Test
    void acceptsAnAbsentOrNullNickname() throws IOException, InterruptedException {
        assertThat(post(valid().with("nickname", ABSENT)).statusCode()).isEqualTo(201);
        assertThat(post(valid().with("nickname", null)).statusCode()).isEqualTo(201);
    }

    private static JsonNode assertValidationFailed(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("type").asString()).isEqualTo("tag:bitmonkey.co.nz,2026:problem/validation-failed");
        assertThat(problem.get("code").asString()).isEqualTo("validation-failed");
        assertThat(problem.get("instance").asString()).isEqualTo(PATH);
        return problem;
    }

    private static List<String> errors(JsonNode problem) {
        return problem.get("errors")
                .valueStream()
                .map(error ->
                        error.get("field").asString() + ":" + error.get("code").asString())
                .toList();
    }

    private static Body valid() {
        return new Body(json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen")
                .put("nickname", "Rainy Day Fund"));
    }

    private record Body(ObjectNode node) {

        Body with(String field, @Nullable String value) {
            ObjectNode copy = node.deepCopy();
            if (ABSENT.equals(value)) {
                copy.remove(field);
            } else if (value == null) {
                copy.putNull(field);
            } else {
                copy.put(field, value);
            }
            return new Body(copy);
        }
    }

    private HttpResponse<String> post(Body body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json.writeValueAsString(body.node())))
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
