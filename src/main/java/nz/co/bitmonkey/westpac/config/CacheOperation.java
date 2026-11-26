// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

/** The cache operations whose failures are counted, each with the permanent code of its metric tag. */
enum CacheOperation {
    GET("get"),
    PUT("put");

    private final String code;

    CacheOperation(String code) {
        this.code = code;
    }

    /** Gives the code as the {@code operation} tag carries it, for example {@code get}. */
    String code() {
        return code;
    }
}
