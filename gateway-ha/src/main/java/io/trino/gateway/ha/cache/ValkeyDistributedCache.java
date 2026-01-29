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
import io.trino.gateway.ha.config.ValkeyConfiguration;
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

    public ValkeyDistributedCache(ValkeyConfiguration config)
    {
        this.enabled = config.isEnabled();
        this.cacheTtlSeconds = config.getCacheTtlSeconds();

        if (enabled) {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(config.getMaxTotal());
            poolConfig.setMaxIdle(config.getMaxIdle());
            poolConfig.setMinIdle(config.getMinIdle());
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMinEvictableIdleDuration(Duration.ofSeconds(60));
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
            poolConfig.setNumTestsPerEvictionRun(3);
            poolConfig.setBlockWhenExhausted(true);

            String password = config.getPassword();
            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, config.getHost(), config.getPort(),
                        config.getTimeoutMs(), password, config.getDatabase());
            }
            else {
                this.jedisPool = new JedisPool(poolConfig, config.getHost(), config.getPort(), config.getTimeoutMs());
            }

            log.info("Valkey distributed cache initialized: %s:%d (database: %d)",
                    config.getHost(), config.getPort(), config.getDatabase());
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
