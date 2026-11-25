// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisConnectionTest extends ApplicationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void writesAKeyThatTheTestContainerHolds() throws Exception {
        String key = "westpac:test:" + UUID.randomUUID();

        redis.opsForValue().set(key, "value");
        try {
            assertThat(redisCli("get", key)).isEqualTo("value");
        } finally {
            redis.delete(key);
        }
    }
}
