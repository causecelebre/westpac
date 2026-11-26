// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.List;
import java.util.Optional;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import nz.co.bitmonkey.westpac.config.AccountNumberProperties;
import org.springframework.stereotype.Component;

/**
 * Chooses the number of a customer's next account. A customer has one account base at each branch, and the suffix
 * counts the accounts already at that branch. A new base comes from the account number sequence.
 */
@Component
final class AccountNumberGenerator {

    private final AccountRepository repository;

    /**
     * Makes the generator.
     *
     * @param repository the source of sequence values
     * @param properties the configured bank
     * @throws IllegalStateException if the configured bank is not {@code 03}
     */
    AccountNumberGenerator(AccountRepository repository, AccountNumberProperties properties) {
        if (!Bank03.CODE.equals(properties.bank())) {
            throw new IllegalStateException(String.format(
                    "westpac.account-number.bank must be %s, the only bank the service holds branches for, got %s",
                    Bank03.CODE, properties.bank()));
        }
        this.repository = repository;
    }

    /**
     * Gives the number for a customer's next account at a branch.
     *
     * <p>A customer with an account at {@code branch} gets that account's base, with the count of their accounts at
     * {@code branch} as the suffix. A customer with none gets a new base and suffix {@code 000}. A sequence value whose
     * check digit would be 10 is skipped.
     *
     * @param accounts the accounts the customer holds now
     * @param branch the branch of the new account
     * @return the number of the new account
     */
    AccountNumber numberFor(CustomerAccounts accounts, BranchNumber branch) {
        List<AccountNumber> atBranch = accounts.numbers().stream()
                .filter(number -> number.branch().equals(branch))
                .toList();

        if (!atBranch.isEmpty()) {
            AccountNumber first = atBranch.getFirst();
            return new AccountNumber(first.bank(), branch, first.base(), atBranch.size());
        }

        Optional<AccountNumber> number = Optional.empty();
        while (number.isEmpty()) {
            number = AccountNumber.withNewBase(branch, repository.nextAccountNumberSequence());
        }

        return number.get();
    }
}
