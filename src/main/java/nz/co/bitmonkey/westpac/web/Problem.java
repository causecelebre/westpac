// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** The kinds of problem the service answers with, each with its HTTP status, permanent code and title. */
public enum Problem {
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "account-not-found", "The account does not exist"),
    BRANCH_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "branch-not-allowed", "The branch is not allowed"),
    ACCOUNT_LIMIT_REACHED(
            HttpStatus.UNPROCESSABLE_CONTENT, "account-limit-reached", "The customer has the most accounts allowed");

    private static final String TYPE_PREFIX = "tag:bitmonkey.co.nz,2026:problem/";

    private final HttpStatus status;
    private final String code;
    private final String title;

    Problem(HttpStatus status, String code, String title) {
        this.status = status;
        this.code = code;
        this.title = title;
    }

    /**
     * Builds the RFC 9457 body for this problem, with the request path as its instance.
     *
     * @param request the request that the problem answers
     * @param detail text for people about this occurrence
     * @return the problem body
     */
    public ProblemDetail body(HttpServletRequest request, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
