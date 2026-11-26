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
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.AccountIdGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CacheTest extends ApplicationTest {

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
    void servesAReadFromTheCacheAfterTheRowIsGone() throws Exception {
        String id = create();
        HttpResponse<String> first = get(id);
        asOwner()
                .sql("delete from savings_account where id = ?")
                .param(UUID.fromString(id))
                .update();

        HttpResponse<String> second = get(id);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(json.readTree(second.body())).isEqualTo(json.readTree(first.body()));
    }

    @Test
    void doesNotCacheAMiss() throws Exception {
        String id = new AccountIdGenerator(Clock.systemUTC()).next().value().toString();

        HttpResponse<String> read = get(id);

        assertThat(read.statusCode()).isEqualTo(404);
        assertThat(redisCli("exists", key(id))).isEqualTo("0");
    }

    @Test
    void cachesAnAccountOnItsFirstReadAndNotOnItsCreate() throws Exception {
        String id = create();

        assertThat(redisCli("exists", key(id))).isEqualTo("0");

        get(id);

        assertThat(redisCli("exists", key(id))).isEqualTo("1");
    }

    @Test
    void namesTheKeyWithTheVersionedPrefixAndTheAccountId() throws Exception {
        String id = create();

        get(id);

        assertThat(redisCli("keys", "*" + id + "*")).isEqualTo("westpac:v1:account:" + id);
    }

    @Test
    void keepsTheEntryForTenMinutes() throws Exception {
        String id = create();

        get(id);

        assertThat(Long.parseLong(redisCli("ttl", key(id)))).isGreaterThan(590).isLessThanOrEqualTo(600);
    }

    @Test
    void storesTheAccountAsJsonWithNoTypeInformation() throws Exception {
        String id = create();

        get(id);

        JsonNode value = json.readTree(redisCli("get", key(id)));
        assertThat(value.isObject()).isTrue();
        assertThat(fieldNames(value)).isNotEmpty().noneMatch(name -> name.startsWith("@"));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (String name : node.propertyNames()) {
            names.add(name);
            names.addAll(fieldNames(node.get(name)));
        }
        return names;
    }

    private static String key(String id) {
        return "westpac:v1:account:" + id;
    }

    private String create() throws IOException, InterruptedException {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen")
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
