// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires a string to hold {@code min} to {@code max} Unicode code points. {@code @Size} counts UTF-16 units, so it
 * counts a character outside the Basic Multilingual Plane as two. A {@code null} is valid.
 *
 * <p>An empty string breaks the rule with the field error code {@code blank}, and any other count out of range with
 * {@code length}.
 */
@Documented
@Constraint(validatedBy = CodePointsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface CodePoints {

    /** The fewest code points allowed. */
    int min();

    /** The most code points allowed. */
    int max();

    /** The message for an empty string. */
    String blankMessage() default "must not be empty or only spaces";

    /** The message, which the validator replaces. */
    String message() default "";

    /** The validation groups. */
    Class<?>[] groups() default {};

    /** The payload. */
    Class<? extends Payload>[] payload() default {};
}
