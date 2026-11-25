// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountWriterServiceTest extends ApplicationTest {

    private static final BranchNumber BRANCH = new BranchNumber("0123");
    private static final BranchNumber OTHER_BRANCH = new BranchNumber("1501");
    private static final CustomerName NAME = new CustomerName("Aroha Ngata");
    private static final Nickname OFFENSIVE = new Nickname("Bullsh1t Fund");

    @Autowired
    private AccountWriterService service;

    @Autowired
    private AccountRepository repository;

    @Test
    void opensFiveAccountsAndRefusesTheSixth() {
        CustomerId customer = newCustomer();
        for (int i = 0; i < CustomerAccountLimit.MAX; i++) {
            assertThat(service.open(request(customer, BRANCH, NAME))).isInstanceOf(OpenResult.Opened.class);
        }

        assertThat(service.open(request(customer, BRANCH, NAME))).isEqualTo(new OpenResult.LimitReached(5));
        assertThat(repository.accountsOf(customer).numbers()).hasSize(5);
    }

    @Test
    void usesOneBaseForEachBranchAndCountsAccountsAtEveryBranch() {
        CustomerId customer = newCustomer();

        AccountNumber first = opened(request(customer, BRANCH, NAME)).number();
        AccountNumber second = opened(request(customer, BRANCH, NAME)).number();
        AccountNumber elsewhere = opened(request(customer, OTHER_BRANCH, NAME)).number();
        AccountNumber third = opened(request(customer, BRANCH, NAME)).number();
        AccountNumber secondElsewhere =
                opened(request(customer, OTHER_BRANCH, NAME)).number();

        assertThat(first.suffix()).isZero();
        assertThat(second).isEqualTo(withSuffix(first, 1));
        assertThat(elsewhere.branch()).isEqualTo(OTHER_BRANCH);
        assertThat(elsewhere.suffix()).isZero();
        assertThat(elsewhere.base()).isNotEqualTo(first.base());
        assertThat(third).isEqualTo(withSuffix(first, 2));
        assertThat(secondElsewhere).isEqualTo(withSuffix(elsewhere, 1));
        assertThat(service.open(request(customer, OTHER_BRANCH, NAME))).isEqualTo(new OpenResult.LimitReached(5));
    }

    @Test
    void refusesABranchTheBankDoesNotOwnAndWritesNoRow() {
        CustomerId customer = newCustomer();

        assertThat(service.open(request(customer, new BranchNumber("1000"), NAME)))
                .isEqualTo(new OpenResult.BranchRefused());
        assertThat(repository.accountsOf(customer).numbers()).isEmpty();
    }

    @Test
    void refusesAnOffensiveNicknameAndWritesNoRow() {
        CustomerId customer = newCustomer();

        assertThat(service.open(new OpenAccount(customer, BRANCH, NAME, OFFENSIVE, null)))
                .isEqualTo(new OpenResult.NicknameRefused());
        assertThat(repository.accountsOf(customer).numbers()).isEmpty();
    }

    @Test
    void checksTheBranchBeforeTheNickname() {
        CustomerId customer = newCustomer();

        assertThat(service.open(new OpenAccount(customer, new BranchNumber("1000"), NAME, OFFENSIVE, null)))
                .isEqualTo(new OpenResult.BranchRefused());
    }

    @Test
    void givesTwoCustomersWithTheSameNameALimitEach() {
        CustomerId one = newCustomer();
        CustomerId other = newCustomer();
        for (int i = 0; i < CustomerAccountLimit.MAX; i++) {
            opened(request(one, BRANCH, NAME));
        }

        assertThat(service.open(request(other, BRANCH, NAME))).isInstanceOf(OpenResult.Opened.class);
    }

    @Test
    void givesOneCustomerWithTwoNamesOneLimit() {
        CustomerId customer = newCustomer();
        List<CustomerName> names = List.of(NAME, new CustomerName("Aroha Smith"));
        for (int i = 0; i < CustomerAccountLimit.MAX; i++) {
            opened(request(customer, BRANCH, names.get(i % 2)));
        }

        assertThat(service.open(request(customer, BRANCH, names.get(1)))).isEqualTo(new OpenResult.LimitReached(5));
    }

    @Test
    void replaysTheSameKeyAndFingerprintWithTheFirstAccount() {
        CustomerId customer = newCustomer();
        Idempotency idempotency = idempotency(customer, NAME);
        SavingsAccount first = opened(keyed(customer, NAME, idempotency));

        assertThat(service.open(keyed(customer, NAME, idempotency))).isEqualTo(new OpenResult.Replayed(first));
        assertThat(repository.accountsOf(customer).numbers()).hasSize(1);
    }

    @Test
    void refusesTheSameKeyWithAnotherFingerprintAndWritesNoRow() {
        CustomerId customer = newCustomer();
        Idempotency idempotency = idempotency(customer, NAME);
        opened(keyed(customer, NAME, idempotency));
        CustomerName other = new CustomerName("Aroha Smith");

        assertThat(service.open(
                        keyed(customer, other, new Idempotency(idempotency.key(), fingerprint(customer, other)))))
                .isEqualTo(new OpenResult.KeyReused());
        assertThat(repository.accountsOf(customer).numbers()).hasSize(1);
    }

    @Test
    void replaysTheFifthAccountRatherThanRefusingAtTheLimit() {
        CustomerId customer = newCustomer();
        for (int i = 1; i < CustomerAccountLimit.MAX; i++) {
            opened(request(customer, BRANCH, NAME));
        }
        Idempotency idempotency = idempotency(customer, NAME);
        SavingsAccount fifth = opened(keyed(customer, NAME, idempotency));

        assertThat(service.open(keyed(customer, NAME, idempotency))).isEqualTo(new OpenResult.Replayed(fifth));
    }

    @Test
    void opensANewAccountForEachRequestWithNoKey() {
        CustomerId customer = newCustomer();

        SavingsAccount first = opened(request(customer, BRANCH, NAME));
        SavingsAccount second = opened(request(customer, BRANCH, NAME));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(repository.accountsOf(customer).numbers()).hasSize(2);
    }

    private SavingsAccount opened(OpenAccount request) {
        OpenResult result = service.open(request);
        assertThat(result).isInstanceOf(OpenResult.Opened.class);
        return ((OpenResult.Opened) result).account();
    }

    private static OpenAccount request(CustomerId customer, BranchNumber branch, CustomerName name) {
        return new OpenAccount(customer, branch, name, null, null);
    }

    private static OpenAccount keyed(CustomerId customer, CustomerName name, Idempotency idempotency) {
        return new OpenAccount(customer, BRANCH, name, null, idempotency);
    }

    private static Idempotency idempotency(CustomerId customer, CustomerName name) {
        return new Idempotency(new IdempotencyKey(UUID.randomUUID()), fingerprint(customer, name));
    }

    private static RequestFingerprint fingerprint(CustomerId customer, CustomerName name) {
        return RequestFingerprint.of(customer, BRANCH, name, null);
    }

    private static CustomerId newCustomer() {
        return new CustomerId(UUID.randomUUID());
    }

    private static AccountNumber withSuffix(AccountNumber number, int suffix) {
        return new AccountNumber(number.bank(), number.branch(), number.base(), suffix);
    }
}
