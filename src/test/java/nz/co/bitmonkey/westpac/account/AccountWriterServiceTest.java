// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class AccountWriterServiceTest extends ApplicationTest {

    private static final BranchNumber BRANCH = new BranchNumber("0123");
    private static final BranchNumber OTHER_BRANCH = new BranchNumber("1501");
    private static final CustomerName NAME = new CustomerName("Aroha Ngata");
    private static final Nickname OFFENSIVE = new Nickname("Bullsh1t Fund");

    @Autowired
    private AccountWriterService service;

    @Autowired
    private AccountRepository repository;

    @Autowired
    private MeterRegistry registry;

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

    @Test
    void countsAnOpenedAccountAsNotReplayed() {
        assertCountsOnly("opened replayed=false", () -> opened(request(newCustomer(), BRANCH, NAME)));
    }

    @Test
    void countsAReplayAsReplayed() {
        CustomerId customer = newCustomer();
        Idempotency idempotency = idempotency(customer, NAME);
        opened(keyed(customer, NAME, idempotency));

        assertCountsOnly("opened replayed=true", () -> service.open(keyed(customer, NAME, idempotency)));
    }

    @Test
    void countsARefusedBranch() {
        assertCountsOnly(
                "refused reason=branch", () -> service.open(request(newCustomer(), new BranchNumber("1000"), NAME)));
    }

    @Test
    void countsARefusedNickname() {
        assertCountsOnly(
                "refused reason=nickname",
                () -> service.open(new OpenAccount(newCustomer(), BRANCH, NAME, OFFENSIVE, null)));
    }

    @Test
    void countsAReachedLimit() {
        CustomerId customer = newCustomer();
        for (int i = 0; i < CustomerAccountLimit.MAX; i++) {
            opened(request(customer, BRANCH, NAME));
        }

        assertCountsOnly("refused reason=limit", () -> service.open(request(customer, BRANCH, NAME)));
    }

    @Test
    void countsAReusedKey() {
        CustomerId customer = newCustomer();
        Idempotency idempotency = idempotency(customer, NAME);
        opened(keyed(customer, NAME, idempotency));
        CustomerName other = new CustomerName("Aroha Smith");

        assertCountsOnly(
                "refused reason=key-reused",
                () -> service.open(
                        keyed(customer, other, new Idempotency(idempotency.key(), fingerprint(customer, other)))));
    }

    @Test
    void countsNothingForAFault() throws Exception {
        Map<String, Double> before = counts();

        withAccountNumbersRunOut(() -> assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> service.open(request(newCustomer(), BRANCH, NAME))));

        assertThat(counts()).isEqualTo(before);
    }

    private void assertCountsOnly(String expected, Runnable action) {
        Map<String, Double> before = counts();

        action.run();

        Map<String, Double> after = counts();
        Map<String, Double> rose = new TreeMap<>();
        after.forEach((name, count) -> {
            double was = before.getOrDefault(name, 0.0);
            if (count > was) {
                rose.put(name, count - was);
            }
        });
        assertThat(rose).containsExactly(Map.entry(expected, 1.0));
    }

    private Map<String, Double> counts() {
        Map<String, Double> counts = new TreeMap<>();
        for (Counter counter : registry.find("westpac.accounts.opened").counters()) {
            counts.put("opened replayed=" + counter.getId().getTag("replayed"), counter.count());
        }
        for (Counter counter : registry.find("westpac.accounts.refused").counters()) {
            counts.put("refused reason=" + counter.getId().getTag("reason"), counter.count());
        }
        assertThat(counts).hasSize(6);

        return counts;
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
