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

import io.airlift.log.Logger;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import io.valkey.exceptions.JedisException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Valkey-based distributed cache implementation with graceful degradation.
 * Falls through to database on Valkey unavailability.
 */
public class ValkeyDistributedCache
        implements DistributedCache
{
    private static final Logger log = Logger.get(ValkeyDistributedCache.class);

    private final JedisPool pool;
    private final boolean enabled;
    private final long healthCheckIntervalMs;
    private volatile boolean healthy = true;
    private volatile long lastHealthCheck;

    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheWrites = new AtomicLong();
    private final AtomicLong cacheErrors = new AtomicLong();

    /**
     * Creates a new Valkey distributed cache.
     *
     * @param host Valkey server host
     * @param port Valkey server port
     * @param password Optional password (can be null)
     * @param database Database index (default 0)
     * @param enabled Whether Valkey is enabled
     * @param maxTotal Maximum total connections in pool
     * @param maxIdle Maximum idle connections
     * @param minIdle Minimum idle connections
     * @param timeoutMs Connection timeout in milliseconds
     * @param healthCheckIntervalMs Health check interval in milliseconds
     */
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
            long healthCheckIntervalMs)
    {
        this.enabled = enabled;
        this.healthCheckIntervalMs = healthCheckIntervalMs;

        if (!enabled) {
            log.info("Valkey distributed cache is disabled");
            this.pool = null;
            this.healthy = false;
            return;
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTime(Duration.ofMinutes(1));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);

        JedisPool tempPool = null;
        try {
            if (password != null && !password.isEmpty()) {
                tempPool = new JedisPool(poolConfig, host, port, timeoutMs, password, database);
            }
            else {
                tempPool = new JedisPool(poolConfig, host, port, timeoutMs, null, database);
            }
            log.info("Valkey distributed cache initialized: %s:%d (database: %d)", host, port, database);

            // Test connection - gracefully degrade if unavailable
            try (Jedis jedis = tempPool.getResource()) {
                jedis.ping();
                log.info("Valkey health check passed");
                this.healthy = true;
            }
            catch (Exception healthCheckException) {
                log.warn(healthCheckException, "Initial Valkey health check failed, will retry later");
                this.healthy = false;
            }
        }
        catch (Exception e) {
            log.error(e, "Failed to create Valkey connection pool, distributed cache will be disabled");
            this.healthy = false;
            if (tempPool != null) {
                tempPool.close();
                tempPool = null;
            }
        }
        this.pool = tempPool;
    }

    @Override
    public Optional<String> get(String key)
    {
        if (!enabled || !healthy) {
            return Optional.empty();
        }

        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            if (value != null) {
                cacheHits.incrementAndGet();
            }
            else {
                cacheMisses.incrementAndGet();
            }
            return Optional.ofNullable(value);
        }
        catch (JedisException e) {
            log.warn(e, "Valkey get failed for key: %s (gracefully degrading)", key);
            cacheErrors.incrementAndGet();
            markUnhealthy();
            return Optional.empty();
        }
    }

    @Override
    public boolean set(String key, String value, long ttlSeconds)
    {
        if (!enabled || !healthy) {
            return false;
        }

        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) {
                jedis.setex(key, ttlSeconds, value);
            }
            else {
                jedis.set(key, value);
            }
            cacheWrites.incrementAndGet();
            return true;
        }
        catch (JedisException e) {
            log.warn(e, "Valkey set failed for key: %s (gracefully degrading)", key);
            cacheErrors.incrementAndGet();
            markUnhealthy();
            return false;
        }
    }

    @Override
    public boolean set(String key, String value)
    {
        return set(key, value, 0);
    }

    @Override
    public boolean delete(String key)
    {
        if (!enabled || !healthy) {
            return false;
        }

        try (Jedis jedis = pool.getResource()) {
            Long deleted = jedis.del(key);
            return deleted != null && deleted > 0;
        }
        catch (JedisException e) {
            log.warn(e, "Valkey delete failed for key: %s (gracefully degrading)", key);
            markUnhealthy();
            return false;
        }
    }

    @Override
    public long delete(String... keys)
    {
        if (!enabled || !healthy || keys == null || keys.length == 0) {
            return 0;
        }

        try (Jedis jedis = pool.getResource()) {
            Long deleted = jedis.del(keys);
            return deleted != null ? deleted : 0;
        }
        catch (JedisException e) {
            log.warn(e, "Valkey bulk delete failed (gracefully degrading)");
            markUnhealthy();
            return 0;
        }
    }

    @Override
    public boolean isHealthy()
    {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck > healthCheckIntervalMs) {
            checkHealth();
        }
        return healthy;
    }

    private void checkHealth()
    {
        if (!enabled || pool == null) {
            healthy = false;
            lastHealthCheck = System.currentTimeMillis();
            return;
        }

        try (Jedis jedis = pool.getResource()) {
            String pong = jedis.ping();
            healthy = "PONG".equals(pong);
            if (healthy) {
                log.debug("Valkey health check passed");
            }
        }
        catch (Exception e) {
            log.warn(e, "Valkey health check failed");
            healthy = false;
        }
        finally {
            lastHealthCheck = System.currentTimeMillis();
        }
    }

    private void markUnhealthy()
    {
        if (healthy) {
            log.warn("Marking Valkey as unhealthy due to operation failure");
            healthy = false;
            lastHealthCheck = 0; // Force immediate health check on next operation
        }
    }

    @Override
    public void close()
    {
        if (pool != null && !pool.isClosed()) {
            log.info("Closing Valkey connection pool");
            pool.close();
        }
    }

    /**
     * Returns the number of cache hits.
     */
    public long getCacheHits()
    {
        return cacheHits.get();
    }

    /**
     * Returns the number of cache misses.
     */
    public long getCacheMisses()
    {
        return cacheMisses.get();
    }

    /**
     * Returns the number of successful cache writes.
     */
    public long getCacheWrites()
    {
        return cacheWrites.get();
    }

    /**
     * Returns the number of cache operation errors.
     */
    public long getCacheErrors()
    {
        return cacheErrors.get();
    }

    /**
     * Returns the cache hit rate as a percentage (0-100).
     * Returns 0 if no operations have occurred.
     */
    public double getCacheHitRate()
    {
        long total = cacheHits.get() + cacheMisses.get();
        return total == 0 ? 0.0 : (cacheHits.get() * 100.0 / total);
    }
}
