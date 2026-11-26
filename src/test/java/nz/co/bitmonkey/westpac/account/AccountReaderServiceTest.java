// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;
import nz.co.bitmonkey.westpac.ApplicationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class AccountReaderServiceTest extends ApplicationTest {

    @Autowired
    private AccountReaderService reader;

    @Autowired
    private AccountWriterService writer;

    @Test
    void findsAnOpenedAccountEqualToTheOpenedResult() {
        OpenResult result = writer.open(new OpenAccount(
                new CustomerId(UUID.randomUUID()),
                new BranchNumber("0123"),
                new CustomerName("Aroha Ngata"),
                new Nickname("Rainy day"),
                null));
        assertThat(result).isInstanceOf(OpenResult.Opened.class);
        SavingsAccount opened = ((OpenResult.Opened) result).account();

        assertThat(reader.find(opened.id())).contains(opened);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givesTheOpenedAccountOnASecondReadFromTheCache(boolean withNickname) {
        OpenResult result = writer.open(new OpenAccount(
                new CustomerId(UUID.randomUUID()),
                new BranchNumber("0123"),
                new CustomerName("Aroha Ngata"),
                withNickname ? new Nickname("Rainy day") : null,
                null));
        assertThat(result).isInstanceOf(OpenResult.Opened.class);
        SavingsAccount opened = ((OpenResult.Opened) result).account();

        assertThat(reader.find(opened.id())).contains(opened);
        assertThat(reader.find(opened.id())).contains(opened);
    }

    @Test
    void givesEmptyForAnUnknownId() {
        AccountId unknown = new AccountIdGenerator(Clock.systemUTC()).next();

        assertThat(reader.find(unknown)).isEmpty();
    }
}
