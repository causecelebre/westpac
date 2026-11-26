// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.exc.StreamConstraintsException;

/** Turns the exceptions that requests can cause into problem bodies. */
@RestControllerAdvice
final class ProblemAdvice extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemAdvice.class);

    private static final Map<String, FieldErrorCode> CODES_BY_CONSTRAINT =
            Map.of("NotNull", FieldErrorCode.REQUIRED, "Pattern", FieldErrorCode.CHARACTERS);

    private static final String RETRY_AFTER_SECONDS = "5";

    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private static final Set<String> RACE_CONSTRAINTS =
            Set.of("savings_account_customer_uk", "savings_account_number_uk", "savings_account_idempotent_uk");

    /** Answers broken field rules with {@code 400 validation-failed}, one error for each failing field. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldErrorBody> errors = exception.getFieldErrors().stream()
                .collect(Collectors.groupingBy(FieldError::getField, TreeMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(ProblemAdvice::firstError)
                .toList();

        ProblemDetail problem = Problem.VALIDATION_FAILED.body(
                ((ServletWebRequest) request).getRequest(),
                errors.size() == 1 ? "1 field is not valid" : errors.size() + " fields are not valid");
        problem.setProperty("errors", errors);

        return ResponseEntity.of(problem).headers(headers).build();
    }

    /**
     * Answers a body over the size limit with {@code 413 request-too-large}. Answers a body that is not JSON, not an
     * object, or holds a duplicate key, an unknown property or a bad value with {@code 400 malformed-request}.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.getCause() instanceof StreamConstraintsException) {
            return problem(Problem.REQUEST_TOO_LARGE, headers, request, "The body is larger than the service accepts");
        }

        return problem(
                Problem.MALFORMED_REQUEST,
                headers,
                request,
                "The body is not a JSON object with only the known properties and values of their types");
    }

    /** Answers a path variable that does not convert to its type, such as an id that is not a UUID. */
    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(
                Problem.MALFORMED_REQUEST,
                headers,
                request,
                "The path variable " + exception.getPropertyName() + " is not valid");
    }

    /** Answers a body that is not {@code application/json}. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problem(Problem.UNSUPPORTED_MEDIA_TYPE, headers, request, "The body must be application/json");
    }

    /** Answers {@code 406} with no body, because the client accepts no type that a problem can be written in. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(status).headers(headers).build();
    }

    /**
     * Answers a create that lost a race with another create for the same customer with {@code 409 concurrent-request}.
     * A duplicate on any other constraint is a fault, and gets {@code 500 internal-error}.
     *
     * @param exception the broken unique constraint
     * @param request the request that met it
     * @return the problem
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException exception, HttpServletRequest request) {
        if (!RACE_CONSTRAINTS.contains(constraintOf(exception))) {
            return handleUnplanned(exception, request);
        }

        return ResponseEntity.of(Problem.CONCURRENT_REQUEST.body(
                        request, "Another create for the customer was completed first. Send the request again"))
                .build();
    }

    /**
     * Answers a database that cannot be reached, or that did not answer in time, with {@code 503 service-unavailable}
     * and {@code Retry-After: 5}. A transaction that cannot get a connection when it starts counts as one that cannot
     * reach the database. So does a transaction whose rollback or commit fails, because its connection was lost.
     *
     * @param exception the failure from the database
     * @param request the request that met it
     * @return the problem
     */
    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        TransientDataAccessException.class,
        CannotCreateTransactionException.class,
        TransactionSystemException.class
    })
    ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception exception, HttpServletRequest request) {
        return ResponseEntity.of(Problem.SERVICE_UNAVAILABLE.body(
                        request, "The database is not available now. Send the request again later"))
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .build();
    }

    /**
     * Answers a lock wait that ran past the lock timeout, PostgreSQL's SQL state {@code 55P03}, as a database that did
     * not answer in time. Any other SQL error that Spring cannot classify is a fault, and gets {@code 500
     * internal-error}.
     *
     * @param exception the SQL error that Spring did not classify
     * @param request the request that met it
     * @return the problem
     */
    @ExceptionHandler(UncategorizedSQLException.class)
    ResponseEntity<ProblemDetail> handleUncategorizedSql(
            UncategorizedSQLException exception, HttpServletRequest request) {
        SQLException cause = exception.getSQLException();
        if (cause != null && LOCK_NOT_AVAILABLE.equals(cause.getSQLState())) {
            return handleDatabaseUnavailable(exception, request);
        }

        return handleUnplanned(exception, request);
    }

    /**
     * Answers any exception that no other handler takes with {@code 500 internal-error}, and logs it in full.
     *
     * @param exception the unplanned fault
     * @param request the request that met it
     * @return the problem, which names nothing of the fault
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnplanned(Exception exception, HttpServletRequest request) {
        LOG.error("Unplanned fault, answered with 500 internal-error", exception);
        return ResponseEntity.of(Problem.INTERNAL_ERROR.body(request, "The service met an unplanned fault"))
                .build();
    }

    private static ResponseEntity<Object> problem(
            Problem problem, HttpHeaders headers, WebRequest request, String detail) {
        return ResponseEntity.of(problem.body(((ServletWebRequest) request).getRequest(), detail))
                .headers(headers)
                .build();
    }

    private static String constraintOf(DuplicateKeyException exception) {
        if (exception.getMostSpecificCause() instanceof PSQLException postgres) {
            ServerErrorMessage message = postgres.getServerErrorMessage();
            if (message != null) {
                return String.valueOf(message.getConstraint());
            }
        }

        return "";
    }

    private static FieldErrorBody firstError(List<FieldError> errorsOfOneField) {
        FieldError first = errorsOfOneField.stream()
                .min(Comparator.comparing(ProblemAdvice::codeOf))
                .orElseThrow();
        return new FieldErrorBody(first.getField(), codeOf(first).code(), String.valueOf(first.getDefaultMessage()));
    }

    private static FieldErrorCode codeOf(FieldError error) {
        ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);
        HibernateConstraintViolation<?> hibernate = violation.unwrap(HibernateConstraintViolation.class);
        FieldErrorCode dynamic = hibernate.getDynamicPayload(FieldErrorCode.class);

        if (dynamic != null) {
            return dynamic;
        }

        FieldErrorCode code = CODES_BY_CONSTRAINT.get(error.getCode());
        if (code == null) {
            throw new IllegalStateException("no field error code for constraint " + error.getCode());
        }

        return code;
    }

    /**
     * One entry of the {@code errors} list of a {@code validation-failed} problem.
     *
     * @param field the request property
     * @param code the field error code
     * @param message text for people
     */
    record FieldErrorBody(String field, String code, String message) {}
}
