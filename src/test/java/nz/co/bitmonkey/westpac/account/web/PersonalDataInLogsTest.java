// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class PersonalDataInLogsTest extends ApplicationTest {

    private static final String CUSTOMER_NAME = "Wiremu Logtester";
    private static final String NICKNAME = "Private Savings";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.shared();

    @Autowired
    private Environment environment;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void logsTheAccountIdButNoCustomerIdNameOrNicknameForACreateAndItsReplay(CapturedOutput output) throws Exception {
        UUID customer = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        HttpResponse<String> created = create(customer, key);
        HttpResponse<String> replayed = create(customer, key);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(replayed.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        String accountId = json.readTree(created.body()).get("id").asString();
        assertThat(output.getOut().lines())
                .anyMatch(line -> line.contains("Account opened") && line.contains(accountId));
        assertThat(output.getOut())
                .doesNotContainIgnoringCase(customer.toString())
                .doesNotContain(CUSTOMER_NAME)
                .doesNotContain("Wiremu")
                .doesNotContain(NICKNAME);
    }

    private HttpResponse<String> create(UUID customer, String key) throws Exception {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("customerId", customer.toString())
                .put("branch", "0123")
                .put("customerName", CUSTOMER_NAME)
                .put("nickname", NICKNAME));
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:"
                                + environment.getRequiredProperty("local.server.port") + "/api/v1/savings-accounts"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", key)
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
    }
}
