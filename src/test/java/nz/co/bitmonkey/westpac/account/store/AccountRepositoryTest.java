// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.AccountId;
import nz.co.bitmonkey.westpac.account.AccountNumber;
import nz.co.bitmonkey.westpac.account.BranchNumber;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.CustomerName;
import nz.co.bitmonkey.westpac.account.Idempotency;
import nz.co.bitmonkey.westpac.account.IdempotencyKey;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.account.RequestFingerprint;
import nz.co.bitmonkey.westpac.account.SavingsAccount;
import nz.co.bitmonkey.westpac.account.StoredAccount;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountRepositoryTest extends ApplicationTest {

    private static final BranchNumber BRANCH = new BranchNumber("0123");

    @Autowired
    private AccountRepository repository;

    @Test
    void readsBackAnAccountWithNoNickname() {
        SavingsAccount account = account(new CustomerId(UUID.randomUUID()), number(7), null);

        repository.insert(account, 1, null);

        assertThat(repository.findById(account.id())).contains(account);
    }

    @Test
    void readsBackAnAccountWithANickname() {
        SavingsAccount account = account(new CustomerId(UUID.randomUUID()), number(7), new Nickname("Holiday fund"));

        repository.insert(account, 1, null);

        assertThat(repository.findById(account.id())).contains(account);
    }

    @Test
    void findsNoAccountForAnUnknownId() {
        assertThat(repository.findById(newAccountId())).isEmpty();
    }

    @Test
    void givesACustomersNumbersInPositionOrder() {
        CustomerId customer = new CustomerId(UUID.randomUUID());
        AccountNumber first = number(30);
        AccountNumber second = withSuffix(first, 20);
        AccountNumber third = withSuffix(first, 10);

        repository.insert(account(customer, third, null), 3, null);
        repository.insert(account(customer, first, null), 1, null);
        repository.insert(account(customer, second, null), 2, null);

        assertThat(repository.accountsOf(customer).numbers()).containsExactly(first, second, third);
    }

    @Test
    void findsAKeyedAccountWithItsFingerprint() {
        CustomerId customer = new CustomerId(UUID.randomUUID());
        SavingsAccount account = account(customer, number(7), new Nickname("Holiday fund"));
        Idempotency idempotency = idempotency();

        repository.insert(account, 1, idempotency);

        assertThat(repository.findByKey(customer, idempotency.key()))
                .contains(new StoredAccount(account, idempotency.fingerprint()));
    }

    @Test
    void findsNoAccountForAnUnknownKey() {
        CustomerId customer = new CustomerId(UUID.randomUUID());
        repository.insert(account(customer, number(7), null), 1, idempotency());

        assertThat(repository.findByKey(customer, new IdempotencyKey(UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    void findsNoAccountForTheSameKeyOfAnotherCustomer() {
        Idempotency idempotency = idempotency();
        repository.insert(account(new CustomerId(UUID.randomUUID()), number(7), null), 1, idempotency);

        assertThat(repository.findByKey(new CustomerId(UUID.randomUUID()), idempotency.key()))
                .isEmpty();
    }

    @Test
    void neverFindsAnAccountOpenedWithNoKey() {
        CustomerId customer = new CustomerId(UUID.randomUUID());
        repository.insert(account(customer, number(7), null), 1, null);

        assertThat(repository.findByKey(customer, new IdempotencyKey(UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    void givesRisingSequenceValues() {
        int first = repository.nextAccountNumberSequence();
        int second = repository.nextAccountNumberSequence();

        assertThat(second).isGreaterThan(first);
    }

    private AccountNumber number(int suffix) {
        while (true) {
            Optional<AccountNumber> base =
                    AccountNumber.withNewBase("03", BRANCH, repository.nextAccountNumberSequence());
            if (base.isPresent()) {
                return withSuffix(base.get(), suffix);
            }
        }
    }

    private static AccountNumber withSuffix(AccountNumber number, int suffix) {
        return new AccountNumber(number.bank(), number.branch(), number.base(), suffix);
    }

    private static SavingsAccount account(CustomerId customer, AccountNumber number, @Nullable Nickname nickname) {
        return new SavingsAccount(
                newAccountId(), number, customer, new CustomerName("Repository Test"), nickname, Instant.now());
    }

    private static Idempotency idempotency() {
        UUID key = UUID.randomUUID();
        return new Idempotency(
                new IdempotencyKey(key),
                new RequestFingerprint(key.toString().replace("-", "").repeat(2)));
    }

    private static AccountId newAccountId() {
        UUID random = UUID.randomUUID();
        return new AccountId(
                new UUID((random.getMostSignificantBits() & ~0xF000L) | 0x7000L, random.getLeastSignificantBits()));
    }
}
