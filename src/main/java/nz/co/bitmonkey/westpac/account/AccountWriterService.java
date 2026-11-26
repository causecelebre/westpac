// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import nz.co.bitmonkey.westpac.account.store.AccountRepository;
import nz.co.bitmonkey.westpac.moderation.NicknamePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opens savings accounts. */
@Service
public final class AccountWriterService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountWriterService.class);

    private static final int TRANSACTION_TIMEOUT_SECONDS = 5;

    private final AccountRepository repository;
    private final AccountIdGenerator ids;
    private final AccountNumberGenerator numbers;
    private final NicknamePolicy nicknames;
    private final Clock clock;
    private final TransactionTemplate transactions;

    /**
     * Makes the service.
     *
     * @param repository the store of accounts
     * @param ids the source of account ids
     * @param numbers the source of account numbers
     * @param nicknames the policy that decides whether a nickname is allowed
     * @param clock the clock that gives the time an account is opened
     * @param transactionManager the manager of the transaction that holds the count, the number and the insert
     */
    AccountWriterService(
            AccountRepository repository,
            AccountIdGenerator ids,
            AccountNumberGenerator numbers,
            NicknamePolicy nicknames,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.ids = ids;
        this.numbers = numbers;
        this.nicknames = nicknames;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
    }

    /**
     * Opens a savings account, and lets the database refuse a sixth account for one customer.
     *
     * <p>This needs only a count and an insert, and a sequence call when the account base is new. That is fewer SQL
     * calls than the lock or counter options. Creates do not queue behind a lock. Only two creates for the same
     * customer at the same moment wait, and then only until the first one commits.
     *
     * <p>Other ways to enforce the limit were considered and not used:
     *
     * <ul>
     *   <li>Advisory lock per customer: more code and database calls, and it only removes a rare 409.
     *   <li>SERIALIZABLE transactions: needs retry code, and its failures are harder to predict.
     *   <li>A single writer thread: works with one instance only, and every customer waits in one queue.
     *   <li>A lock in Redis or in memory: Redis is optional, and a memory lock does not work across instances.
     *   <li>A counter table: no 409s, but it needs a second table, and the requirements ask for one.
     * </ul>
     *
     * @param request the account to open
     * @return the new account, the account that an earlier request with the same idempotency key opened, or why none
     *     was opened
     */
    public OpenResult open(OpenAccount request) {
        OpenResult result = decide(request);
        if (result instanceof OpenResult.Opened newAccount) {
            // TODO(#12): record who opened the account in an audit trail, once callers are authenticated.
            LOG.info("Account opened: accountId={}", newAccount.account().id().value());
        }

        return result;
    }

    private OpenResult decide(OpenAccount request) {
        if (!Bank03.allows(request.branch())) {
            return new OpenResult.BranchRefused();
        }

        Nickname nickname = request.nickname();
        if (nickname != null && !nicknames.allows(nickname)) {
            return new OpenResult.NicknameRefused();
        }

        OpenResult result = transactions.execute(status -> {
            Idempotency idempotency = request.idempotency();
            if (idempotency != null) {
                Optional<StoredAccount> stored = repository.findByKey(request.customerId(), idempotency.key());
                if (stored.isPresent()) {
                    return replayOrRefuse(stored.get(), idempotency.fingerprint());
                }
            }

            CustomerAccounts accounts = repository.accountsOf(request.customerId());

            if (accounts.numbers().size() >= CustomerAccountLimit.MAX) {
                return new OpenResult.LimitReached(CustomerAccountLimit.MAX);
            }

            SavingsAccount account = new SavingsAccount(
                    ids.next(),
                    numbers.numberFor(accounts, request.branch()),
                    request.customerId(),
                    request.name(),
                    request.nickname(),
                    Instant.now(clock));
            repository.insert(account, accounts.numbers().size() + 1, request.idempotency());

            return new OpenResult.Opened(account);
        });

        return result;
    }

    private static OpenResult replayOrRefuse(StoredAccount stored, RequestFingerprint fingerprint) {
        if (stored.fingerprint().equals(fingerprint)) {
            return new OpenResult.Replayed(stored.account());
        }

        return new OpenResult.KeyReused();
    }
}
