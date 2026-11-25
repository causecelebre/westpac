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
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

class SameKeyRaceTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final int CREATES = 10;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void opensOneAccountForTenCreatesHeldAtOneLockWithOneKeyAndEachRetryReplaysIt() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            try (Connection owner = ownerTransaction();
                    Statement lock = owner.createStatement()) {
                lock.execute("lock table savings_account in share mode");
                for (int i = 0; i < CREATES; i++) {
                    futures.add(threads.submit(() -> create(customer, key)));
                }
                awaitAppSessionsWaitingOnALock(CREATES);
                owner.commit();
            }
        }

        List<HttpResponse<String>> answers = new ArrayList<>();
        for (Future<HttpResponse<String>> future : futures) {
            answers.add(future.get());
        }

        assertThat(answers).extracting(HttpResponse::statusCode).containsOnly(201, 409);
        assertThat(answers).filteredOn(answer -> answer.statusCode() == 409).hasSize(CREATES - 1);
        assertThat(rowsOf(customer)).isEqualTo(1);
        String id = onlyIdOf(customer);
        HttpResponse<String> winner = answers.stream()
                .filter(answer -> answer.statusCode() == 201)
                .findFirst()
                .orElseThrow();
        assertThat(idOf(winner)).isEqualTo(id);
        assertThat(winner.headers().firstValue("Idempotent-Replayed")).isEmpty();

        for (HttpResponse<String> answer : answers) {
            if (answer.statusCode() == 409) {
                HttpResponse<String> retry = create(customer, key);

                assertThat(retry.statusCode()).isEqualTo(201);
                assertThat(idOf(retry)).isEqualTo(id);
                assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
            }
        }

        assertThat(rowsOf(customer)).isEqualTo(1);
    }

    private HttpResponse<String> create(UUID customer, UUID key) throws IOException, InterruptedException {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + PATH))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key.toString())
                .POST(BodyPublishers.ofString(body))
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    private static String idOf(HttpResponse<String> response) {
        return json.readTree(response.body()).get("id").asString();
    }

    private static int rowsOf(UUID customer) {
        return asOwner()
                .sql("select count(*) from savings_account where customer_id = :customer")
                .param("customer", customer)
                .query(Integer.class)
                .single();
    }

    private static String onlyIdOf(UUID customer) {
        return asOwner()
                .sql("select id::text from savings_account where customer_id = :customer")
                .param("customer", customer)
                .query(String.class)
                .single();
    }
}
