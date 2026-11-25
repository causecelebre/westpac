// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import nz.co.bitmonkey.westpac.web.FieldErrorCode;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/** Checks {@link CodePoints}, and gives the field error code as the violation's dynamic payload. */
final class CodePointsValidator implements ConstraintValidator<CodePoints, String> {

    private int min;
    private int max;
    private String blankMessage = "";

    @Override
    public void initialize(CodePoints rule) {
        min = rule.min();
        max = rule.max();
        blankMessage = rule.blankMessage();
    }

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        int count = value.codePointCount(0, value.length());
        if (count >= min && count <= max) {
            return true;
        }

        boolean blank = value.isEmpty();
        context.disableDefaultConstraintViolation();
        context.unwrap(HibernateConstraintValidatorContext.class)
                .withDynamicPayload(blank ? FieldErrorCode.BLANK : FieldErrorCode.LENGTH)
                .buildConstraintViolationWithTemplate(blank ? blankMessage : lengthMessage())
                .addConstraintViolation();
        return false;
    }

    private String lengthMessage() {
        return min == max ? "must be " + min + " characters" : "must be " + min + " to " + max + " characters";
    }
}
