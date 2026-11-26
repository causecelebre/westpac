// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class GracefulShutdownTest {

    private static final Duration REQUEST_STARTED = Duration.ofMillis(300);
    private static final Duration BODY_DELAY = Duration.ofSeconds(2);

    @Test
    void finishesARequestInProgressAndRefusesNewConnectionsDuringTheGracePeriod() throws Exception {
        Map<String, Object> properties = new HashMap<>(ApplicationTest.storeProperties());
        properties.put("APP_PORT", 0);
        properties.put("MANAGEMENT_PORT", 0);
        ConfigurableApplicationContext context = new SpringApplicationBuilder(WestpacApplication.class)
                .properties(properties)
                .registerShutdownHook(false)
                .run();
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        byte[] body = ("{\"customerId\":\"" + UUID.randomUUID()
                        + "\",\"branch\":\"0123\",\"customerName\":\"Jane Citizen\"}")
                .getBytes(StandardCharsets.UTF_8);
        int half = body.length / 2;
        Thread closer = Thread.ofPlatform().unstarted(context::close);
        String statusLine;

        try (Socket slow = new Socket("localhost", port)) {
            OutputStream out = slow.getOutputStream();
            out.write(("POST /api/v1/savings-accounts HTTP/1.1\r\nHost: localhost\r\n"
                            + "Content-Type: application/json\r\nContent-Length: " + body.length
                            + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write(body, 0, half);
            out.flush();
            Thread.sleep(REQUEST_STARTED.toMillis());

            closer.start();
            Thread.sleep(BODY_DELAY.toMillis());

            assertThatExceptionOfType(ConnectException.class).isThrownBy(() -> {
                try (Socket fresh = new Socket()) {
                    fresh.connect(new InetSocketAddress("localhost", port), 2000);
                }
            });

            out.write(body, half, body.length - half);
            out.flush();
            slow.setSoTimeout(10_000);
            statusLine = new BufferedReader(new InputStreamReader(slow.getInputStream(), StandardCharsets.US_ASCII))
                    .readLine();
        } finally {
            if (!closer.isAlive()) {
                context.close();
            }
        }

        assertThat(statusLine).startsWith("HTTP/1.1 201");
        assertThat(closer.join(Duration.ofSeconds(5))).isTrue();
    }
}
