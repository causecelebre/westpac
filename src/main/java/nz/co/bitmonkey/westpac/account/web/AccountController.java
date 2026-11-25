// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import nz.co.bitmonkey.westpac.account.AccountId;
import nz.co.bitmonkey.westpac.account.AccountReaderService;
import nz.co.bitmonkey.westpac.account.AccountWriterService;
import nz.co.bitmonkey.westpac.account.BranchNumber;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.CustomerName;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.account.OpenAccount;
import nz.co.bitmonkey.westpac.account.OpenResult;
import nz.co.bitmonkey.westpac.web.Problem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opens savings accounts and reads them by id, over HTTP. */
@RestController
@RequestMapping(AccountController.PATH)
public final class AccountController {

    static final String PATH = "/api/v1/savings-accounts";

    private final AccountWriterService writer;
    private final AccountReaderService reader;

    /**
     * Makes the controller.
     *
     * @param writer the service that opens accounts
     * @param reader the service that reads accounts
     */
    AccountController(AccountWriterService writer, AccountReaderService reader) {
        this.writer = writer;
        this.reader = reader;
    }

    /**
     * Opens a savings account.
     *
     * @param request the account to open
     * @param http the HTTP request
     * @return {@code 201} with the new account, or {@code 422} with the rule that refused it
     */
    @PostMapping
    public ResponseEntity<?> open(@Valid @RequestBody OpenAccountRequest request, HttpServletRequest http) {
        OpenAccount open = openAccount(request);

        return switch (writer.open(open)) {
            case OpenResult.Opened(var account) ->
                ResponseEntity.created(URI.create(PATH + "/" + account.id().value()))
                        .body(AccountResponse.of(account));
            case OpenResult.BranchRefused() ->
                problem(
                        Problem.BRANCH_NOT_ALLOWED,
                        http,
                        "Branch " + open.branch().value() + " is outside the bank's branch ranges");
            case OpenResult.LimitReached(var limit) ->
                problem(Problem.ACCOUNT_LIMIT_REACHED, http, "The customer already has " + limit + " accounts");
        };
    }

    /**
     * Reads one savings account.
     *
     * @param id the account id
     * @param http the HTTP request
     * @return {@code 200} with the account, or {@code 404} when no account has that id. A UUID that is not version 7
     *     gets {@code 404} with no lookup.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, HttpServletRequest http) {
        return AccountId.from(id)
                .flatMap(reader::find)
                .<ResponseEntity<?>>map(account -> ResponseEntity.ok(AccountResponse.of(account)))
                .orElseGet(() -> notFound(http));
    }

    /**
     * Gives a validated request in domain types, with no idempotency key.
     *
     * @throws NullPointerException if a required component is {@code null}, which Bean Validation refuses first
     */
    private static OpenAccount openAccount(OpenAccountRequest request) {
        return new OpenAccount(
                new CustomerId(Objects.requireNonNull(request.customerId(), "customerId")),
                new BranchNumber(Objects.requireNonNull(request.branch(), "branch")),
                new CustomerName(Objects.requireNonNull(request.customerName(), "customerName")),
                Optional.ofNullable(request.nickname()).map(Nickname::new).orElse(null),
                null);
    }

    private static ResponseEntity<?> notFound(HttpServletRequest http) {
        return problem(Problem.ACCOUNT_NOT_FOUND, http, "No account has this id");
    }

    private static ResponseEntity<?> problem(Problem problem, HttpServletRequest http, String detail) {
        return ResponseEntity.of(problem.body(http, detail)).build();
    }
}
