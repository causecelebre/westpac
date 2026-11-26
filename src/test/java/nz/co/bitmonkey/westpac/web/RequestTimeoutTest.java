// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class RequestTimeoutTest extends ApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void closesTheConnectionOfAClientThatStopsSendingAfterTenSeconds() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        Duration took;

        try (Socket slow = new Socket("localhost", port)) {
            slow.setSoTimeout(30_000);
            slow.getOutputStream().write("POST /api".getBytes(StandardCharsets.US_ASCII));
            slow.getOutputStream().flush();

            long start = System.nanoTime();
            InputStream in = slow.getInputStream();
            while (in.read() != -1) {
                // reads anything the server sends before it closes the connection
            }
            took = Duration.ofNanos(System.nanoTime() - start);
        }

        assertThat(took).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(12));
    }
}
