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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class TestValkeyDistributedCache
{
    private ValkeyDistributedCache cache;

    @AfterEach
    void cleanup()
    {
        if (cache != null) {
            cache.close();
        }
    }

    @Test
    void testDisabledCacheReturnsEmptyAndFails()
    {
        // Create disabled cache
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false, // disabled
                20,
                10,
                5,
                2000,
                30000);

        // All operations should fail gracefully
        assertThat(cache.get("key")).isEmpty();
        assertThat(cache.set("key", "value")).isFalse();
        assertThat(cache.set("key", "value", 100)).isFalse();
        assertThat(cache.delete("key")).isFalse();
        assertThat(cache.delete("key1", "key2")).isEqualTo(0);
        assertThat(cache.isHealthy()).isFalse();
    }

    @Test
    void testEnabledCacheWithInvalidHostIsUnhealthy()
    {
        // JedisPool creation is lazy - it doesn't fail immediately
        // but the health check will fail
        cache = new ValkeyDistributedCache(
                "invalid-host-that-does-not-exist-12345",
                6379,
                null,
                0,
                true,
                20,
                10,
                5,
                100,
                30000); // Very short timeout to make test fast

        // Cache should be marked unhealthy after failed health check
        assertThat(cache.isHealthy()).isFalse();
        assertThat(cache.get("key")).isEmpty();
        assertThat(cache.set("key", "value")).isFalse();
    }

    @Test
    void testDisabledCacheCloseDoesNothing()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        // Should not throw exception
        cache.close();
    }

    @Test
    void testGetReturnsEmptyWhenUnhealthy()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        Optional<String> result = cache.get("testKey");
        assertThat(result).isEmpty();
    }

    @Test
    void testSetReturnsFalseWhenUnhealthy()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        boolean result = cache.set("testKey", "testValue", 3600);
        assertThat(result).isFalse();
    }

    @Test
    void testSetWithoutTtlReturnsFalseWhenUnhealthy()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        boolean result = cache.set("testKey", "testValue");
        assertThat(result).isFalse();
    }

    @Test
    void testDeleteSingleKeyReturnsFalseWhenUnhealthy()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        boolean result = cache.delete("testKey");
        assertThat(result).isFalse();
    }

    @Test
    void testDeleteMultipleKeysReturnsZeroWhenUnhealthy()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        long result = cache.delete("key1", "key2", "key3");
        assertThat(result).isEqualTo(0);
    }

    @Test
    void testDeleteMultipleKeysWithNullArray()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        long result = cache.delete((String[]) null);
        assertThat(result).isEqualTo(0);
    }

    @Test
    void testDeleteMultipleKeysWithEmptyArray()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        long result = cache.delete();
        assertThat(result).isEqualTo(0);
    }

    @Test
    void testIsHealthyReturnsFalseWhenDisabled()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        assertThat(cache.isHealthy()).isFalse();
    }

    @Test
    void testSetWithZeroTtlBehavesLikeNoTtl()
    {
        cache = new ValkeyDistributedCache(
                "localhost",
                6379,
                null,
                0,
                false,
                20,
                10,
                5,
                2000,
                30000);

        boolean resultWithZero = cache.set("key1", "value", 0);
        boolean resultWithoutTtl = cache.set("key2", "value");

        // Both should return false when disabled
        assertThat(resultWithZero).isEqualTo(resultWithoutTtl);
    }

    @Test
    void testCacheOperationsWithPassword()
    {
        // Test that password configuration is accepted
        cache = new ValkeyDistributedCache(
                "invalid-host",
                6379,
                "test-password",
                0,
                true,
                20,
                10,
                5,
                100,
                30000);

        // Cache should be unhealthy but initialized
        assertThat(cache.isHealthy()).isFalse();
    }

    @Test
    void testCacheOperationsWithDatabase()
    {
        // Test that database configuration is accepted
        cache = new ValkeyDistributedCache(
                "invalid-host",
                6379,
                null,
                5, // database index
                true,
                20,
                10,
                5,
                100,
                30000);

        // Cache should be unhealthy but initialized
        assertThat(cache.isHealthy()).isFalse();
    }

    @Test
    void testCachePoolConfiguration()
    {
        // Test that pool configuration parameters are accepted
        cache = new ValkeyDistributedCache(
                "invalid-host",
                6379,
                null,
                0,
                true,
                50, // maxTotal
                25, // maxIdle
                10, // minIdle
                5000, // timeoutMs
                30000); // healthCheckIntervalMs

        // Cache should be unhealthy but initialized
        assertThat(cache.isHealthy()).isFalse();
    }
}
