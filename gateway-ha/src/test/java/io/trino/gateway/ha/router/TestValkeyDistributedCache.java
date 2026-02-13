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
import io.trino.gateway.ha.cache.QueryMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class TestValkeyDistributedCache
{
    @Test
    void testDisabledCache()
    {
        // When distributed cache is disabled, NoopDistributedCache should be used instead
        NoopDistributedCache cache = new NoopDistributedCache();

        assertThat(cache.get("test-key")).isEmpty();

        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        cache.set("test-key", metadata);
        assertThat(cache.get("test-key")).isEmpty();
    }

    @Test
    void testNoopDistributedCache()
    {
        NoopDistributedCache cache = new NoopDistributedCache();

        assertThat(cache.get("any-key")).isEmpty();

        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        cache.set("key", metadata);
        assertThat(cache.get("key")).isEmpty();

        cache.invalidate("key");
    }
}
