// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives each request an id, which the response header, every log line and every problem body carry. A client's
 * {@code X-Request-Id} is kept only if it has the accepted form, so a client cannot write into the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";

    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    private static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = idOf(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String idOf(@Nullable String header) {
        if (header != null && ACCEPTED.matcher(header).matches()) {
            return header;
        }

        return UUID.randomUUID().toString();
    }
}
