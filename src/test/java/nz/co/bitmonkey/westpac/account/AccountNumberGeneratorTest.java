// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.co.bitmonkey.westpac.ApplicationTest;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import nz.co.bitmonkey.westpac.config.AccountNumberProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

class AccountNumberGeneratorTest extends ApplicationTest {

    private static final BranchNumber BRANCH = new BranchNumber("0123");
    private static final BranchNumber OTHER_BRANCH = new BranchNumber("1501");

    @Autowired
    private AccountNumberGenerator generator;

    @Autowired
    private AccountRepository repository;

    @Test
    void givesANewBaseWithSuffixZeroAtANewBranch() {
        AccountNumber atOtherBranch = generator.numberFor(new CustomerAccounts(List.of()), OTHER_BRANCH);

        AccountNumber number = generator.numberFor(new CustomerAccounts(List.of(atOtherBranch)), BRANCH);

        assertThat(number.branch()).isEqualTo(BRANCH);
        assertThat(number.suffix()).isZero();
        assertThat(number.base()).isNotEqualTo(atOtherBranch.base());
    }

    @Test
    void usesTheBranchBaseWithTheCountAtThatBranchAsTheSuffix() {
        AccountNumber first = generator.numberFor(new CustomerAccounts(List.of()), BRANCH);
        AccountNumber second = withSuffix(first, 1);
        AccountNumber elsewhere = generator.numberFor(new CustomerAccounts(List.of(first, second)), OTHER_BRANCH);

        AccountNumber number = generator.numberFor(new CustomerAccounts(List.of(first, elsewhere, second)), BRANCH);

        assertThat(number).isEqualTo(withSuffix(first, 2));
    }

    @Test
    void givesTwoCustomersAtOneBranchDifferentBases() {
        AccountNumber one = generator.numberFor(new CustomerAccounts(List.of()), BRANCH);
        AccountNumber other = generator.numberFor(new CustomerAccounts(List.of()), BRANCH);

        assertThat(other.base()).isNotEqualTo(one.base());
    }

    @Test
    void skipsASequenceValueWhoseCheckDigitWouldBeTen() {
        JdbcClient owner = asOwner();
        int skipped = repository.nextAccountNumberSequence() + 1;
        while (AccountNumber.withNewBase(Bank03.CODE, OTHER_BRANCH, skipped).isPresent()) {
            skipped++;
        }
        int used = skipped + 1;
        while (AccountNumber.withNewBase(Bank03.CODE, OTHER_BRANCH, used).isEmpty()) {
            used++;
        }
        owner.sql("select setval('account_number_seq', :value)")
                .param("value", skipped - 1)
                .query()
                .singleValue();

        AccountNumber number = generator.numberFor(new CustomerAccounts(List.of()), OTHER_BRANCH);

        assertThat(number)
                .isEqualTo(AccountNumber.withNewBase(Bank03.CODE, OTHER_BRANCH, used)
                        .orElseThrow());
        assertThat(owner.sql("select last_value from account_number_seq")
                        .query(Integer.class)
                        .single())
                .isEqualTo(used);
    }

    @Test
    void refusesToStartForAnotherBank() {
        new ApplicationContextRunner()
                .withUserConfiguration(Generator.class)
                .withBean(AccountRepository.class, () -> repository)
                .withPropertyValues("westpac.account-number.bank=01")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageEndingWith("got 01"));
    }

    private static AccountNumber withSuffix(AccountNumber number, int suffix) {
        return new AccountNumber(number.bank(), number.branch(), number.base(), suffix);
    }

    @EnableConfigurationProperties(AccountNumberProperties.class)
    @Import(AccountNumberGenerator.class)
    static class Generator {}
}
