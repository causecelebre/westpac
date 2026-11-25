// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

class ConcurrentLimitTest extends ApplicationTest {

    private static final String PATH = "/api/v1/savings-accounts";
    private static final int CREATES = 20;
    private static final int FIRST_BRANCH = 101;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void opensAtMostFiveAccountsForTwentyCreatesAtOnceAtTwentyBranches() throws Exception {
        UUID customer = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CREATES; i++) {
                String branch = String.format(Locale.ROOT, "%04d", FIRST_BRANCH + i);
                statuses.add(threads.submit(() -> {
                    start.await();
                    return create(customer, branch);
                }));
            }
            start.countDown();
        }

        List<Integer> answers = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            answers.add(status.get());
        }

        assertThat(rowsOf(customer)).isLessThanOrEqualTo(5);
        assertThat(answers).allSatisfy(status -> assertThat(status).isIn(201, 409, 422));
        assertThat(answers).filteredOn(status -> status == 201).hasSizeLessThanOrEqualTo(5);
        assertThat(answers).contains(409);

        for (int i = 0; i < CREATES; i++) {
            if (answers.get(i) == 409) {
                String branch = String.format(Locale.ROOT, "%04d", FIRST_BRANCH + i);
                assertThat(create(customer, branch)).isIn(201, 422);
            }
        }

        assertThat(rowsOf(customer)).isEqualTo(5);
    }

    private int create(UUID customer, String branch) throws IOException, InterruptedException {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", branch)
                .put("customerName", "Jane Citizen"));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        return client.send(request, BodyHandlers.discarding()).statusCode();
    }

    private static int rowsOf(UUID customer) {
        return asOwner()
                .sql("select count(*) from savings_account where customer_id = :customer")
                .param("customer", customer)
                .query(Integer.class)
                .single();
    }
}
