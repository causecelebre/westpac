// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.account;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.UUIDClock;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Makes the id of a new account, a UUID version 7 whose timestamp comes from the injected clock. */
@Component
public final class AccountIdGenerator {

    private final NoArgGenerator generator;

    /**
     * Makes the generator.
     *
     * @param clock the clock that gives the time in each id
     */
    public AccountIdGenerator(Clock clock) {
        this.generator = Generators.timeBasedEpochGenerator(new SecureRandom(), new InjectedClock(clock));
    }

    /**
     * Makes a new account id.
     *
     * @return a version 7 id, different from every id made before
     */
    public AccountId next() {
        return new AccountId(generator.generate());
    }

    private static final class InjectedClock extends UUIDClock {

        private final Clock clock;

        InjectedClock(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long currentTimeMillis() {
            return clock.millis();
        }
    }
}
