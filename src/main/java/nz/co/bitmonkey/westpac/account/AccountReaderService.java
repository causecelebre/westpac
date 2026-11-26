// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.util.Optional;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Reads savings accounts by id, through the account cache. Spring proxies the class for the cache, so it is not final.
 */
@Service
public class AccountReaderService {

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
     * Finds one account, from the cache when it holds the account. An account that is found is cached, and an empty
     * result is not.
     *
     * @param id the account id
     * @return the account, or empty when no account has that id
     */
    @Cacheable(cacheNames = "account", key = "#id.value().toString()", unless = "#result == null")
    public Optional<SavingsAccount> find(AccountId id) {
        return repository.findById(id);
    }
}
