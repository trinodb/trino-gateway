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
package io.trino.gateway.ha.cache;

import io.airlift.log.Logger;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import io.valkey.exceptions.JedisException;

import java.time.Duration;
import java.util.Optional;

public class ValkeyDistributedCache
        implements Cache
{
    private static final Logger log = Logger.get(ValkeyDistributedCache.class);

    private final JedisPool jedisPool;
    private final boolean enabled;
    private final long cacheTtlSeconds;

    public ValkeyDistributedCache(
            String host,
            int port,
            String password,
            int database,
            boolean enabled,
            int maxTotal,
            int maxIdle,
            int minIdle,
            int timeoutMs,
            long cacheTtlSeconds)
    {
        this.enabled = enabled;
        this.cacheTtlSeconds = cacheTtlSeconds;

        if (enabled) {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(maxTotal);
            poolConfig.setMaxIdle(maxIdle);
            poolConfig.setMinIdle(minIdle);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMinEvictableIdleDuration(Duration.ofSeconds(60));
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
            poolConfig.setNumTestsPerEvictionRun(3);
            poolConfig.setBlockWhenExhausted(true);

            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, timeoutMs, password, database);
            }
            else {
                this.jedisPool = new JedisPool(poolConfig, host, port, timeoutMs);
            }

            log.info("Valkey distributed cache initialized: %s:%d (database: %d)", host, port, database);
        }
        else {
            this.jedisPool = null;
            log.info("Valkey distributed cache is disabled");
        }
    }

    @Override
    public Optional<String> get(String key)
    {
        if (!enabled) {
            return Optional.empty();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key);
            if (value != null) {
                return Optional.of(value);
            }
            return Optional.empty();
        }
        catch (JedisException e) {
            log.warn(e, "Failed to get key from Valkey: %s", key);
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, String value)
    {
        if (!enabled) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, cacheTtlSeconds, value);
        }
        catch (JedisException e) {
            log.warn(e, "Failed to set key in Valkey: %s", key);
        }
    }

    @Override
    public void invalidate(String key)
    {
        if (!enabled) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
        catch (JedisException e) {
            log.warn(e, "Failed to invalidate key in Valkey: %s", key);
        }
    }

    @Override
    public boolean isEnabled()
    {
        return enabled;
    }

    public void close()
    {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
