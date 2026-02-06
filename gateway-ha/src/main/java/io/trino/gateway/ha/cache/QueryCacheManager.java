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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Manages all cache operations for query metadata (L1 local + L2 distributed).
 * This class encapsulates the complete 3-tier caching strategy:
 * L1: Local Caffeine cache (per instance)
 * L2: Distributed cache (Valkey/Redis - shared across instances)
 * L3: Database/fallback loader
 */
public class QueryCacheManager
{
    private static final String BACKEND_KEY_PREFIX = "trino:query:backend:";
    private static final String ROUTING_GROUP_KEY_PREFIX = "trino:query:routing_group:";
    private static final String EXTERNAL_URL_KEY_PREFIX = "trino:query:external_url:";

    private final Cache distributedCache;
    private final LoadingCache<String, String> backendCache;
    private final LoadingCache<String, String> routingGroupCache;
    private final LoadingCache<String, String> externalUrlCache;

    public QueryCacheManager(
            Function<String, String> backendLoader,
            Function<String, String> routingGroupLoader,
            Function<String, String> externalUrlLoader,
            Cache distributedCache)
    {
        this.distributedCache = requireNonNull(distributedCache, "distributedCache is null");
        this.backendCache = buildCache(backendLoader);
        this.routingGroupCache = buildCache(routingGroupLoader);
        this.externalUrlCache = buildCache(externalUrlLoader);
    }

    private LoadingCache<String, String> buildCache(Function<String, String> loader)
    {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build(loader::apply);
    }

    // Backend cache operations

    public String getBackendFromL1(String queryId)
    {
        return backendCache.get(queryId);
    }

    public void setBackendInL1(String queryId, String backend)
    {
        backendCache.put(queryId, backend);
    }

    public void setBackend(String queryId, String backend)
    {
        backendCache.put(queryId, backend);
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
        }
    }

    public Optional<String> getCachedBackend(String queryId)
    {
        if (!distributedCache.isEnabled()) {
            return Optional.empty();
        }
        return distributedCache.get(BACKEND_KEY_PREFIX + queryId);
    }

    public void cacheBackend(String queryId, String backend)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
        }
    }

    // Routing group cache operations

    public String getRoutingGroupFromL1(String queryId)
    {
        return routingGroupCache.get(queryId);
    }

    public void setRoutingGroupInL1(String queryId, String routingGroup)
    {
        routingGroupCache.put(queryId, routingGroup);
    }

    public void setRoutingGroup(String queryId, String routingGroup)
    {
        routingGroupCache.put(queryId, routingGroup);
        if (distributedCache.isEnabled()) {
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
        }
    }

    public Optional<String> getCachedRoutingGroup(String queryId)
    {
        if (!distributedCache.isEnabled()) {
            return Optional.empty();
        }
        return distributedCache.get(ROUTING_GROUP_KEY_PREFIX + queryId);
    }

    public void cacheRoutingGroup(String queryId, String routingGroup)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
        }
    }

    // External URL cache operations

    public String getExternalUrlFromL1(String queryId)
    {
        return externalUrlCache.get(queryId);
    }

    public void setExternalUrlInL1(String queryId, String externalUrl)
    {
        externalUrlCache.put(queryId, externalUrl);
    }

    public void setExternalUrl(String queryId, String externalUrl)
    {
        externalUrlCache.put(queryId, externalUrl);
        if (distributedCache.isEnabled()) {
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }

    public Optional<String> getCachedExternalUrl(String queryId)
    {
        if (!distributedCache.isEnabled()) {
            return Optional.empty();
        }
        return distributedCache.get(EXTERNAL_URL_KEY_PREFIX + queryId);
    }

    public void cacheExternalUrl(String queryId, String externalUrl)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }

    // Batch operations

    public void updateAllCaches(String queryId, String backend, String routingGroup, String externalUrl)
    {
        backendCache.put(queryId, backend);
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }
}
