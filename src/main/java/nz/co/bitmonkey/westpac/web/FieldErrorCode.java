// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.web;

import java.util.Locale;

/**
 * The permanent codes of a field error, in the order they apply. A field with several broken rules reports the first.
 */
public enum FieldErrorCode {
    REQUIRED,
    BLANK,
    LENGTH,
    CHARACTERS;

    /** Gives the code as clients read it, for example {@code required}. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
