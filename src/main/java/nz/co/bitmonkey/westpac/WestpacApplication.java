// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the savings account service. */
@SpringBootApplication(proxyBeanMethods = false)
public final class WestpacApplication {

    private WestpacApplication() {}

    public static void main(String[] args) {
        SpringApplication.run(WestpacApplication.class, args);
    }
}
