// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import nz.co.bitmonkey.westpac.account.AccountId;
import nz.co.bitmonkey.westpac.account.AccountNumber;
import nz.co.bitmonkey.westpac.account.BranchNumber;
import nz.co.bitmonkey.westpac.account.CustomerAccounts;
import nz.co.bitmonkey.westpac.account.CustomerId;
import nz.co.bitmonkey.westpac.account.CustomerName;
import nz.co.bitmonkey.westpac.account.Idempotency;
import nz.co.bitmonkey.westpac.account.Nickname;
import nz.co.bitmonkey.westpac.account.SavingsAccount;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads and writes savings accounts with plain SQL through {@code JdbcClient}.
 *
 * <p>JPA is not used, for four reasons:
 *
 * <ul>
 *   <li>One table and a few simple queries do not need a large framework.
 *   <li>JPA entities need setters and a no-argument constructor, and this code uses immutable records.
 *   <li>Lazy loading, change tracking and table relationships add nothing for one unchanging table.
 *   <li>The count, the insert with its position and the sequence call are clearest as plain SQL.
 * </ul>
 */
@Component
public final class AccountRepository {

    private static final RowMapper<AccountNumber> NUMBER = (rs, row) -> number(rs);

    private static final RowMapper<SavingsAccount> ACCOUNT = (rs, row) -> account(rs);

    private final JdbcClient jdbc;

    /**
     * Makes the repository.
     *
     * @param jdbc the client that runs the SQL
     */
    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reads the account numbers a customer holds.
     *
     * @param customerId the customer
     * @return the customer's account numbers, in the order the accounts were opened
     */
    public CustomerAccounts accountsOf(CustomerId customerId) {
        return new CustomerAccounts(jdbc.sql("""
                        select bank, branch, account_base, suffix
                        from savings_account
                        where customer_id = :customerId
                        order by customer_account_seq
                        """)
                .param("customerId", customerId.value())
                .query(NUMBER)
                .list());
    }

    /**
     * Stores a new account.
     *
     * @param account the account
     * @param position the account's place among the customer's accounts, from 1
     * @param idempotency the request's idempotency key and fingerprint, or {@code null} when the request had no key
     */
    public void insert(SavingsAccount account, int position, @Nullable Idempotency idempotency) {
        AccountNumber number = account.number();
        Optional<Idempotency> keyed = Optional.ofNullable(idempotency);

        jdbc.sql("""
                        insert into savings_account (id, bank, branch, account_base, suffix, customer_id,
                            customer_name, customer_account_seq, nickname, idempotency_key, request_fingerprint,
                            created_at)
                        values (:id, :bank, :branch, :base, :suffix, :customerId, :customerName, :position,
                            :nickname, :idempotencyKey, :fingerprint, :createdAt)
                        """)
                .param("id", account.id().value())
                .param("bank", number.bank())
                .param("branch", number.branch().value())
                .param("base", number.base())
                .param("suffix", number.suffix())
                .param("customerId", account.customerId().value())
                .param("customerName", account.name().value())
                .param("position", position)
                .param(
                        "nickname",
                        Optional.ofNullable(account.nickname())
                                .map(Nickname::value)
                                .orElse(null))
                .param("idempotencyKey", keyed.map(i -> i.key().value()).orElse(null))
                .param("fingerprint", keyed.map(i -> i.fingerprint().value()).orElse(null))
                .param("createdAt", OffsetDateTime.ofInstant(account.createdAt(), ZoneOffset.UTC))
                .update();
    }

    /**
     * Reads one account.
     *
     * @param id the account id
     * @return the account, or empty when no account has that id
     */
    public Optional<SavingsAccount> findById(AccountId id) {
        return jdbc.sql("""
                        select id, bank, branch, account_base, suffix, customer_id, customer_name, nickname,
                            created_at
                        from savings_account
                        where id = :id
                        """).param("id", id.value()).query(ACCOUNT).optional();
    }

    /**
     * Takes the next value of the account number sequence.
     *
     * @return the value, from 1 to 999999
     */
    public int nextAccountNumberSequence() {
        return jdbc.sql("select nextval('account_number_seq')")
                .query(Integer.class)
                .single();
    }

    private static AccountNumber number(ResultSet rs) throws SQLException {
        return new AccountNumber(
                rs.getString("bank"),
                new BranchNumber(rs.getString("branch")),
                rs.getString("account_base"),
                rs.getInt("suffix"));
    }

    private static SavingsAccount account(ResultSet rs) throws SQLException {
        return new SavingsAccount(
                new AccountId(rs.getObject("id", UUID.class)),
                number(rs),
                new CustomerId(rs.getObject("customer_id", UUID.class)),
                new CustomerName(rs.getString("customer_name")),
                Optional.ofNullable(rs.getString("nickname")).map(Nickname::new).orElse(null),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
