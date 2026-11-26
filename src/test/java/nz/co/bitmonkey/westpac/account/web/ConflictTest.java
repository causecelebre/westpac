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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.AccountIdGenerator;
import nz.co.bitmonkey.westpac.account.AccountNumber;
import nz.co.bitmonkey.westpac.account.BranchNumber;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.CustomerName;
import nz.co.bitmonkey.westpac.account.RequestFingerprint;
import org.jspecify.annotations.Nullable;
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
    private static final AccountIdGenerator ids = new AccountIdGenerator(Clock.systemUTC());

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

        HttpResponse<String> retry = retryAfterClashWithNewKey(ownerRow(customer, 5, "0123", base, 1));

        assertThat(json.readTree(retry.body()).get("accountNumber").asString()).isEqualTo("03-0123-" + base + "-002");
    }

    @Test
    void answersAClashOnTheCustomerPositionWith409AndTheRetryOpensTheNextAccount() throws Exception {
        UUID customer = UUID.randomUUID();
        String first = openFirstAccount(customer);

        HttpResponse<String> retry = retryAfterClashWithNewKey(ownerRow(customer, 2, "1501", unreachedBase("1501"), 0));

        assertThat(json.readTree(retry.body()).get("accountNumber").asString())
                .isEqualTo(first.substring(0, first.length() - 3) + "001");
    }

    @Test
    void answersAClashOnTheIdempotencyKeyWith409AndTheRetryReplaysTheOwnerRow() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        RequestFingerprint fingerprint = RequestFingerprint.of(
                new CustomerId(customer), new BranchNumber("0123"), new CustomerName("Jane Citizen"), null);
        OwnerRow row = new OwnerRow(
                ids.next().value(), customer, 2, "0123", unreachedBase("0123"), 0, key, fingerprint.value());

        HttpResponse<String> retry = retryAfterClash(key, row);

        assertThat(json.readTree(retry.body()).get("id").asString())
                .isEqualTo(row.id().toString());
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(rowsOf(customer)).isEqualTo(1);
    }

    private HttpResponse<String> retryAfterClashWithNewKey(OwnerRow row) throws Exception {
        HttpResponse<String> retry = retryAfterClash(UUID.randomUUID(), row);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).isEmpty();
        return retry;
    }

    private HttpResponse<String> retryAfterClash(UUID key, OwnerRow row) throws Exception {
        CompletableFuture<HttpResponse<String>> create;
        try (Connection owner = ownerTransaction()) {
            insertRow(owner, row);
            create = client.sendAsync(createRequest(row.customer(), key), BodyHandlers.ofString());
            awaitAppSessionsWaitingOnALock(1);
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

        HttpResponse<String> retry = client.send(createRequest(row.customer(), key), BodyHandlers.ofString());

        assertThat(retry.statusCode()).isEqualTo(201);
        return retry;
    }

    private String openFirstAccount(UUID customer) throws IOException, InterruptedException {
        HttpResponse<String> created = client.send(createRequest(customer, UUID.randomUUID()), BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        return json.readTree(created.body()).get("accountNumber").asString();
    }

    private static OwnerRow ownerRow(UUID customer, int position, String branch, String base, int suffix) {
        return new OwnerRow(UUID.randomUUID(), customer, position, branch, base, suffix, null, null);
    }

    private static String unreachedBase(String branch) {
        return IntStream.iterate(900_001, seed -> seed + 1)
                .mapToObj(seed -> AccountNumber.withNewBase(new BranchNumber(branch), seed))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow()
                .base();
    }

    private static void insertRow(Connection owner, OwnerRow row) throws SQLException {
        try (PreparedStatement insert = owner.prepareStatement("""
                insert into savings_account (id, bank, branch, account_base, suffix, customer_id, customer_name,
                    customer_account_seq, idempotency_key, request_fingerprint, created_at)
                values (?, '03', ?, ?, ?, ?, 'Conflict Test', ?, ?, ?, ?)
                """)) {
            insert.setObject(1, row.id());
            insert.setString(2, row.branch());
            insert.setString(3, row.base());
            insert.setInt(4, row.suffix());
            insert.setObject(5, row.customer());
            insert.setInt(6, row.position());
            insert.setObject(7, row.key());
            insert.setString(8, row.fingerprint());
            insert.setObject(9, OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC));
            insert.executeUpdate();
        }
    }

    private static int rowsOf(UUID customer) {
        return asOwner()
                .sql("select count(*) from savings_account where customer_id = :customer")
                .param("customer", customer)
                .query(Integer.class)
                .single();
    }

    private HttpRequest createRequest(UUID customer, UUID key) {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", "0123")
                .put("customerName", "Jane Citizen"));
        return HttpRequest.newBuilder(uri(PATH))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key.toString())
                .POST(BodyPublishers.ofString(body))
                .build();
    }

    private record OwnerRow(
            UUID id,
            UUID customer,
            int position,
            String branch,
            String base,
            int suffix,
            @Nullable UUID key,
            @Nullable String fingerprint) {}

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }
}
