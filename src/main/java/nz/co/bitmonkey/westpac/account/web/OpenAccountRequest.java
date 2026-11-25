// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import java.util.UUID;
import nz.co.bitmonkey.westpac.account.CustomerName;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.account.TextNormaliser;
import org.jspecify.annotations.Nullable;

/**
 * The JSON body of a request to open a savings account. Each component may arrive {@code null} from JSON, and Bean
 * Validation reports it.
 *
 * @param customerId the customer who asks for the account
 * @param branch the branch to open the account at
 * @param customerName the customer's name
 * @param nickname the account's nickname, or {@code null} when the customer gave none
 */
// Error Prone reads Bean Validation's @NotNull as a nullness claim. It is a rule that reports a JSON null, so
// @Nullable is the true type.
@SuppressWarnings("MultipleNullnessAnnotations")
record OpenAccountRequest(
        @NotNull(message = REQUIRED) @Nullable UUID customerId,

        @NotNull(message = REQUIRED) @CodePoints(min = 4, max = 4)
        @Pattern(regexp = "[0-9]+", message = "must hold only the digits 0 to 9") @Nullable String branch,

        @NotNull(message = REQUIRED) @CodePoints(min = 1, max = 100)
        @Pattern(
                regexp = CustomerName.PATTERN,
                message = "must hold only letters, marks, spaces, hyphens, apostrophes and full stops,"
                        + " with at least one letter")
        @Nullable String customerName,

        @CodePoints(min = 5, max = 30, blankMessage = "must not be empty or only spaces; leave it out for no nickname")
        @Pattern(
                regexp = Nickname.PATTERN,
                message = "must hold only letters, marks, digits, spaces, hyphens, apostrophes, full stops" + " and &")
        @Nullable String nickname) {

    private static final String REQUIRED = "must be present";

    /** Makes the request, with the name and the nickname normalised when present. */
    OpenAccountRequest {
        customerName =
                Optional.ofNullable(customerName).map(TextNormaliser::normalise).orElse(null);
        nickname = Optional.ofNullable(nickname).map(TextNormaliser::normalise).orElse(null);
    }
}
