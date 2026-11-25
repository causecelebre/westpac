// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns the exceptions that requests can cause into problem bodies. */
@RestControllerAdvice
final class ProblemAdvice extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemAdvice.class);

    private static final Map<String, FieldErrorCode> CODES_BY_CONSTRAINT =
            Map.of("NotNull", FieldErrorCode.REQUIRED, "Pattern", FieldErrorCode.CHARACTERS);

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

    /** Answers a body that is not JSON, not an object, or holds a duplicate key, an unknown property or a bad value. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
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
