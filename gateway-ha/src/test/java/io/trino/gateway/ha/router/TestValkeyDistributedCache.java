/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.router;

import io.trino.gateway.ha.cache.NoopDistributedCache;
import io.trino.gateway.ha.cache.ValkeyDistributedCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class TestValkeyDistributedCache
{
    @Test
    void testDisabledCache()
    {
        ValkeyDistributedCache cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                1800);

        assertThat(cache.isEnabled()).isFalse();
        assertThat(cache.get("test-key")).isEmpty();

        cache.set("test-key", "test-value");
        assertThat(cache.get("test-key")).isEmpty();

        cache.close();
    }

    @Test
    void testNoopDistributedCache()
    {
        NoopDistributedCache cache = new NoopDistributedCache();

        assertThat(cache.isEnabled()).isFalse();
        assertThat(cache.get("any-key")).isEmpty();

        cache.set("key", "value");
        assertThat(cache.get("key")).isEmpty();

        cache.invalidate("key");
    }
}
