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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.AccountNumber;
import nz.co.bitmonkey.westpac.account.BranchNumber;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConflictTest extends ApplicationTest {

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
    void answersAClashOnTheAccountNumberWith409AndTheRetryOpensTheNextAccount() throws Exception {
        UUID customer = UUID.randomUUID();
        String first = openFirstAccount(customer);
        String base = first.substring("03-0123-".length(), "03-0123-".length() + 7);

        HttpResponse<String> retry = retryAfterClash(customer, 5, "0123", base, 1);

        assertThat(json.readTree(retry.body()).get("accountNumber").asString()).isEqualTo("03-0123-" + base + "-002");
    }

    @Test
    void answersAClashOnTheCustomerPositionWith409AndTheRetryOpensTheNextAccount() throws Exception {
        UUID customer = UUID.randomUUID();
        String first = openFirstAccount(customer);

        String otherBase = AccountNumber.withNewBase("03", new BranchNumber("1501"), 900_001)
                .orElseThrow()
                .base();

        HttpResponse<String> retry = retryAfterClash(customer, 2, "1501", otherBase, 0);

        assertThat(json.readTree(retry.body()).get("accountNumber").asString())
                .isEqualTo(first.substring(0, first.length() - 3) + "001");
    }

    private HttpResponse<String> retryAfterClash(UUID customer, int position, String branch, String base, int suffix)
            throws Exception {
        CompletableFuture<HttpResponse<String>> create;
        try (Connection owner = ownerTransaction()) {
            insertRow(owner, customer, position, branch, base, suffix);
            create = client.sendAsync(createRequest(customer), BodyHandlers.ofString());
            awaitCreateWaitingOnALock();
            owner.commit();
        }

        HttpResponse<String> clashed = create.get(10, TimeUnit.SECONDS);

        assertThat(clashed.statusCode()).isEqualTo(409);
        assertThat(clashed.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        JsonNode problem = json.readTree(clashed.body());
        assertThat(problem.get("type").asString()).isEqualTo("tag:bitmonkey.co.nz,2026:problem/concurrent-request");
        assertThat(problem.get("code").asString()).isEqualTo("concurrent-request");
        assertThat(problem.get("status").asInt()).isEqualTo(409);
        assertThat(problem.get("title").asString()).isNotBlank();
        assertThat(problem.get("detail").asString()).isNotBlank();
        assertThat(problem.get("instance").asString()).isEqualTo(PATH);
        assertThat(clashed.headers().allValues("Cache-Control"))
                .anySatisfy(value -> assertThat(value).contains("no-store"));

        HttpResponse<String> retry = client.send(createRequest(customer), BodyHandlers.ofString());

        assertThat(retry.statusCode()).isEqualTo(201);
        return retry;
    }

    private String openFirstAccount(UUID customer) throws IOException, InterruptedException {
        HttpResponse<String> created = client.send(createRequest(customer), BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        return json.readTree(created.body()).get("accountNumber").asString();
    }

    private static void insertRow(Connection owner, UUID customer, int position, String branch, String base, int suffix)
            throws SQLException {
        try (PreparedStatement insert = owner.prepareStatement("""
                insert into savings_account (id, bank, branch, account_base, suffix, customer_id, customer_name,
                    customer_account_seq, created_at)
                values (?, '03', ?, ?, ?, ?, 'Conflict Test', ?, ?)
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, branch);
            insert.setString(3, base);
            insert.setInt(4, suffix);
            insert.setObject(5, customer);
            insert.setInt(6, position);
            insert.setObject(7, OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC));
            insert.executeUpdate();
        }
    }

    private static void awaitCreateWaitingOnALock() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(4));
        while (Instant.now().isBefore(deadline)) {
            int waiting = asSuperuser().sql("""
                            select count(*) from pg_stat_activity
                            where usename = 'westpac_app' and wait_event_type = 'Lock'
                            """).query(Integer.class).single();
            if (waiting == 1) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the create did not wait on a lock");
    }

    private HttpRequest createRequest(UUID customer) {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        return HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
