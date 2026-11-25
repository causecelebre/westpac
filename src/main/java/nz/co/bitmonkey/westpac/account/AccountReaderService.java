// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.Optional;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import org.springframework.stereotype.Service;

/** Reads savings accounts by id. */
@Service
public final class AccountReaderService {

    private final AccountRepository repository;

    /**
     * Makes the service.
     *
     * @param repository the store of accounts
     */
    AccountReaderService(AccountRepository repository) {
        this.repository = repository;
    }

    /**
     * Finds one account.
     *
     * @param id the account id
     * @return the account, or empty when no account has that id
     */
    public Optional<SavingsAccount> find(AccountId id) {
        return repository.findById(id);
    }
}
