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

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Manages distributed cache operations for query metadata.
 * This class encapsulates L2 (distributed cache) operations.
 */
public class QueryCacheManager
{
    private static final String BACKEND_KEY_PREFIX = "trino:query:backend:";
    private static final String ROUTING_GROUP_KEY_PREFIX = "trino:query:routing_group:";
    private static final String EXTERNAL_URL_KEY_PREFIX = "trino:query:external_url:";

    private final Cache distributedCache;

    public QueryCacheManager(Cache distributedCache)
    {
        this.distributedCache = requireNonNull(distributedCache, "distributedCache is null");
    }

    // Distributed cache operations for backend

    public void cacheBackend(String queryId, String backend)
    {
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

    /**
     * Gets backend from L2 cache, falling back to loader function if not found.
     * Automatically backfills L2 cache on loader hit.
     */
    public String getBackend(String queryId, Function<String, String> loader)
    {
        // Check L2 (distributed cache)
        Optional<String> cached = getCachedBackend(queryId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // L2 miss - call loader (L3 or other fallback)
        String backend = loader.apply(queryId);

        // Backfill L2 cache
        if (!Strings.isNullOrEmpty(backend)) {
            cacheBackend(queryId, backend);
        }

        return backend;
    }

    // Distributed cache operations for routing group

    public void cacheRoutingGroup(String queryId, String routingGroup)
    {
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

    /**
     * Gets routing group from L2 cache, falling back to loader function if not found.
     * Automatically backfills L2 cache on loader hit.
     */
    public String getRoutingGroup(String queryId, Function<String, String> loader)
    {
        // Check L2 (distributed cache)
        Optional<String> cached = getCachedRoutingGroup(queryId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // L2 miss - call loader (L3 or other fallback)
        String routingGroup = loader.apply(queryId);

        // Backfill L2 cache
        if (routingGroup != null) {
            cacheRoutingGroup(queryId, routingGroup);
        }

        return routingGroup;
    }

    // Distributed cache operations for external URL

    public void cacheExternalUrl(String queryId, String externalUrl)
    {
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

    /**
     * Gets external URL from L2 cache, falling back to loader function if not found.
     * Automatically backfills L2 cache on loader hit.
     */
    public String getExternalUrl(String queryId, Function<String, String> loader)
    {
        // Check L2 (distributed cache)
        Optional<String> cached = getCachedExternalUrl(queryId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // L2 miss - call loader (L3 or other fallback)
        String externalUrl = loader.apply(queryId);

        // Backfill L2 cache
        if (externalUrl != null) {
            cacheExternalUrl(queryId, externalUrl);
        }

        return externalUrl;
    }

    // Batch operations

    public void cacheAllQueryMetadata(String queryId, String backend, String routingGroup, String externalUrl)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }
}
