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

import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.DistributedCacheConfiguration;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import io.valkey.exceptions.JedisException;

import java.time.Duration;
import java.util.Optional;

public class ValkeyDistributedCache
        implements DistributedCache
{
    private static final Logger log = Logger.get(ValkeyDistributedCache.class);
    private static final String KEY_PREFIX = "trino:query:";
    private static final JsonCodec<QueryMetadata> JSON_CODEC = JsonCodec.jsonCodec(QueryMetadata.class);

    private final JedisPool jedisPool;
    private final long cacheTtlSeconds;

    public ValkeyDistributedCache(DistributedCacheConfiguration config)
    {
        this.cacheTtlSeconds = (long) config.getCacheTtl().getValue(java.util.concurrent.TimeUnit.SECONDS);

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
        int timeoutMs = (int) config.getTimeout().toMillis();
        int database;
        if (password != null && !password.isEmpty()) {
            database = config.getDatabase();
            this.jedisPool = new JedisPool(poolConfig, config.getHost(), config.getPort(),
                    timeoutMs, password, database);
        }
        else {
            database = 0; // JedisPool uses DEFAULT_DATABASE (0) when no password is provided
            this.jedisPool = new JedisPool(poolConfig, config.getHost(), config.getPort(), timeoutMs);
        }

        log.info("Distributed cache initialized: %s:%d (database: %d)",
                config.getHost(), config.getPort(), database);
    }

    @Override
    public Optional<QueryMetadata> get(String queryId)
    {
        String key = KEY_PREFIX + queryId;
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(key);
            if (json != null) {
                QueryMetadata metadata = JSON_CODEC.fromJson(json);
                log.debug("Retrieved metadata from Valkey for query: %s", queryId);
                return Optional.of(metadata);
            }
            return Optional.empty();
        }
        catch (IllegalArgumentException e) {
            log.error(e, "Failed to deserialize QueryMetadata from Valkey for query: %s", queryId);
            return Optional.empty();
        }
        catch (JedisException e) {
            log.error(e, "Failed to get query metadata from Valkey for query: %s", queryId);
            return Optional.empty();
        }
    }

    @Override
    public void set(String queryId, QueryMetadata metadata)
    {
        String key = KEY_PREFIX + queryId;
        try (Jedis jedis = jedisPool.getResource()) {
            String json = JSON_CODEC.toJson(metadata);
            jedis.setex(key, cacheTtlSeconds, json);
            log.debug("Stored metadata in Valkey for query: %s", queryId);
        }
        catch (IllegalArgumentException e) {
            log.error(e, "Failed to serialize QueryMetadata to JSON for query: %s", queryId);
        }
        catch (JedisException e) {
            log.error(e, "Failed to set query metadata in Valkey for query: %s", queryId);
        }
    }

    @Override
    public void invalidate(String queryId)
    {
        String key = KEY_PREFIX + queryId;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
            log.debug("Invalidated metadata in Valkey for query: %s", queryId);
        }
        catch (JedisException e) {
            log.error(e, "Failed to invalidate query metadata in Valkey for query: %s", queryId);
        }
    }

    public void close()
    {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
