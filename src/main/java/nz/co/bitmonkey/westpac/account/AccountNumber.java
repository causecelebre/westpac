// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A New Zealand bank account number, {@code BB-bbbb-AAAAAAA-SSS}, held as its four parts. Every instance has a valid
 * check digit, so an account number that exists is a valid one.
 *
 * <p>The check digit rule multiplies each of the 16 digits by its weight and requires the sum to divide by 11. Bank
 * {@code 03} uses algorithm A when the base is below {@code 0990000} and algorithm B from there up. The weights are in
 * {@link Bank03}.
 *
 * <p><b>Source.</b> Inland Revenue, <i>Payroll Calculations &amp; Business Rules Specification, 1 April 2026 to 31
 * March 2027</i>, V1.0, 24 March 2026, §7.2. The specification writes an 8-digit base and a 4-digit suffix, with
 * threshold {@code 00990000}. The extra digits are leading zeros with weight zero, so the 16-digit form gives the same
 * sums. Implemented as published, with no variation.
 *
 * @param bank the two-digit bank code; only {@code 03} is accepted
 * @param base the seven-digit account base, with its leading zeros
 * @param suffix the account suffix, from 0 to 999
 */
public record AccountNumber(String bank, BranchNumber branch, String base, int suffix) {

    private static final Pattern SEVEN_DIGITS = Pattern.compile("[0-9]{7}");
    private static final int MAX_SUFFIX = 999;
    private static final int NO_DIGIT = 10;

    /**
     * Makes an account number from its four parts. The messages name the bank and the branch, but never the base.
     *
     * @throws IllegalArgumentException if the bank is not {@code 03}, the base is not seven ASCII digits, the suffix is
     *     outside 0 to 999, or the check digit is wrong
     */
    public AccountNumber {
        if (!Bank03.CODE.equals(bank)) {
            throw new IllegalArgumentException(String.format("account number bank must be 03, got %s", bank));
        }

        if (!SEVEN_DIGITS.matcher(base).matches()) {
            throw new IllegalArgumentException(String.format(
                    "account number base must be seven ASCII digits, at bank %s branch %s", bank, branch.value()));
        }

        if (suffix < 0 || suffix > MAX_SUFFIX) {
            throw new IllegalArgumentException(String.format(
                    "account number suffix must be 0 to 999, got %s at bank %s branch %s",
                    suffix, bank, branch.value()));
        }

        if (weightedSum(bank, branch, base, suffix) % Bank03.MODULUS != 0) {
            throw new IllegalArgumentException(
                    String.format("account number check digit is wrong, at bank %s branch %s", bank, branch.value()));
        }
    }

    /**
     * Makes the number for a new account base at bank {@code 03}, with suffix {@code 000}. The base is the six digits
     * of {@code sequenceValue} followed by the one check digit that makes the number valid.
     *
     * @param branch the branch of the new account
     * @param sequenceValue the next value of the account number sequence, from 1 to 999999
     * @return the new number, or empty when the check digit would have to be 10
     */
    public static Optional<AccountNumber> withNewBase(BranchNumber branch, int sequenceValue) {
        String prefix = String.format(Locale.ROOT, "%06d", sequenceValue);
        int remainder = weightedSum(Bank03.CODE, branch, prefix + "0", 0) % Bank03.MODULUS;
        int checkDigit = (Bank03.MODULUS - remainder) % Bank03.MODULUS;

        if (checkDigit == NO_DIGIT) {
            return Optional.empty();
        }

        return Optional.of(new AccountNumber(Bank03.CODE, branch, prefix + checkDigit, 0));
    }

    /**
     * Gives the standard form, for example {@code 03-0123-0000019-000}.
     *
     * @return the four parts joined by hyphens, with the suffix padded to three digits
     */
    public String standardForm() {
        return String.format(Locale.ROOT, "%s-%s-%s-%03d", bank, branch.value(), base, suffix);
    }

    /**
     * Gives the standard form with the base hidden, for example {@code 03-0123-*******-000}, so that a log line does
     * not show the full number.
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s-%s-*******-%03d", bank, branch.value(), suffix);
    }

    private static int weightedSum(String bank, BranchNumber branch, String base, int suffix) {
        List<Integer> weights = Integer.parseInt(base) < Bank03.ALGORITHM_B_FROM ? Bank03.WEIGHTS_A : Bank03.WEIGHTS_B;
        String digits = String.format(Locale.ROOT, "%s%s%s%03d", bank, branch.value(), base, suffix);

        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * weights.get(i);
        }

        return sum;
    }
}
