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
import com.google.common.base.Strings;
import io.airlift.log.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Manages all cache operations for query metadata (L1 local + L2 distributed + L3 database).
 * This class encapsulates the complete 3-tier caching strategy:
 * L1: Local Caffeine cache (per instance)
 * L2: Distributed cache (Valkey/Redis - shared across instances)
 * L3: Database/fallback loader
 *
 * This class follows the Single Responsibility Principle by handling ALL cache-related
 * logic and orchestration, removing this responsibility from BaseRoutingManager.
 */
public class QueryCacheManager
{
    private static final Logger log = Logger.get(QueryCacheManager.class);
    private static final String BACKEND_KEY_PREFIX = "trino:query:backend:";
    private static final String ROUTING_GROUP_KEY_PREFIX = "trino:query:routing_group:";
    private static final String EXTERNAL_URL_KEY_PREFIX = "trino:query:external_url:";

    private final DistributedCache distributedCache;
    private final com.github.benmanes.caffeine.cache.Cache<String, String> backendCache;
    private final com.github.benmanes.caffeine.cache.Cache<String, String> routingGroupCache;
    private final com.github.benmanes.caffeine.cache.Cache<String, String> externalUrlCache;
    private final QueryCacheLoader cacheLoader;

    /**
     * Interface for loading data from L3 (database/external source) when not found in caches.
     */
    public interface QueryCacheLoader
    {
        /**
         * Load backend from database or external source.
         * May return null if not found.
         */
        String loadBackendFromDatabase(String queryId);

        /**
         * Load routing group from database.
         * May return null if not found.
         */
        String loadRoutingGroupFromDatabase(String queryId);

        /**
         * Load external URL from database.
         * May return null if not found.
         */
        String loadExternalUrlFromDatabase(String queryId);
    }

    public QueryCacheManager(DistributedCache distributedCache, QueryCacheLoader cacheLoader)
    {
        this.distributedCache = requireNonNull(distributedCache, "distributedCache is null");
        this.cacheLoader = requireNonNull(cacheLoader, "cacheLoader is null");
        this.backendCache = buildCache();
        this.routingGroupCache = buildCache();
        this.externalUrlCache = buildCache();
    }

    private com.github.benmanes.caffeine.cache.Cache<String, String> buildCache()
    {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    // L1/L2/L3 fallback loaders - called by Caffeine when key not in L1

    private String loadBackendWithFallback(String queryId)
    {
        // L2: Check distributed cache
        if (distributedCache.isEnabled()) {
            Optional<String> cached = distributedCache.get(BACKEND_KEY_PREFIX + queryId);
            if (cached.isPresent()) {
                log.debug("Backend for query [%s] found in L2 cache", queryId);
                return cached.get();
            }
        }

        // L3: Check database
        String backend = cacheLoader.loadBackendFromDatabase(queryId);
        if (!Strings.isNullOrEmpty(backend)) {
            log.debug("Backend for query [%s] found in L3 (database)", queryId);
            // Backfill L2 cache
            if (distributedCache.isEnabled()) {
                distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
            }
            return backend;
        }

        return null;
    }

    private String loadRoutingGroupWithFallback(String queryId)
    {
        // L2: Check distributed cache
        if (distributedCache.isEnabled()) {
            Optional<String> cached = distributedCache.get(ROUTING_GROUP_KEY_PREFIX + queryId);
            if (cached.isPresent()) {
                log.debug("Routing group for query [%s] found in L2 cache", queryId);
                return cached.get();
            }
        }

        // L3: Check database
        String routingGroup = cacheLoader.loadRoutingGroupFromDatabase(queryId);
        if (!Strings.isNullOrEmpty(routingGroup)) {
            log.debug("Routing group for query [%s] found in L3 (database)", queryId);
            // Backfill L2 cache
            if (distributedCache.isEnabled()) {
                distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
            }
            return routingGroup;
        }

        return null;
    }

    private String loadExternalUrlWithFallback(String queryId)
    {
        // L2: Check distributed cache
        if (distributedCache.isEnabled()) {
            Optional<String> cached = distributedCache.get(EXTERNAL_URL_KEY_PREFIX + queryId);
            if (cached.isPresent()) {
                log.debug("External URL for query [%s] found in L2 cache", queryId);
                return cached.get();
            }
        }

        // L3: Check database
        String externalUrl = cacheLoader.loadExternalUrlFromDatabase(queryId);
        if (!Strings.isNullOrEmpty(externalUrl)) {
            log.debug("External URL for query [%s] found in L3 (database)", queryId);
            // Backfill L2 cache
            if (distributedCache.isEnabled()) {
                distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
            }
            return externalUrl;
        }

        return null;
    }

    // Public API - High-level cache operations with full L1/L2/L3 orchestration

    /**
     * Gets backend for queryId with full L1->L2->L3 fallback.
     * Returns null if not found in any tier.
     */
    public String getBackend(String queryId)
    {
        // Check L1 cache first
        String cached = backendCache.getIfPresent(queryId);
        if (cached != null) {
            return cached;
        }

        // L1 miss: Try L2/L3
        try {
            String loaded = loadBackendWithFallback(queryId);
            if (!Strings.isNullOrEmpty(loaded)) {
                backendCache.put(queryId, loaded);
            }
            return loaded;
        }
        catch (RuntimeException e) {
            log.warn(e, "Exception while loading backend for queryId from cache: %s", queryId);
            return null;
        }
    }

    /**
     * Sets backend in both L1 and L2 caches.
     */
    public void setBackend(String queryId, String backend)
    {
        backendCache.put(queryId, backend);
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
        }
    }

    /**
     * Gets routing group for queryId with full L1->L2->L3 fallback.
     * Returns null if not found in any tier.
     */
    public String getRoutingGroup(String queryId)
    {
        // Check L1 cache first
        String cached = routingGroupCache.getIfPresent(queryId);
        if (cached != null) {
            return cached;
        }

        // L1 miss: Try L2/L3
        try {
            String loaded = loadRoutingGroupWithFallback(queryId);
            if (!Strings.isNullOrEmpty(loaded)) {
                routingGroupCache.put(queryId, loaded);
            }
            return loaded;
        }
        catch (RuntimeException e) {
            log.warn(e, "Exception while loading routing group for queryId from cache: %s", queryId);
            return null;
        }
    }

    /**
     * Sets routing group in both L1 and L2 caches.
     */
    public void setRoutingGroup(String queryId, String routingGroup)
    {
        routingGroupCache.put(queryId, routingGroup);
        if (distributedCache.isEnabled()) {
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
        }
    }

    /**
     * Gets external URL for queryId with full L1->L2->L3 fallback.
     * Returns null if not found in any tier.
     */
    public String getExternalUrl(String queryId)
    {
        // Check L1 cache first
        String cached = externalUrlCache.getIfPresent(queryId);
        if (cached != null) {
            return cached;
        }

        // L1 miss: Try L2/L3
        try {
            String loaded = loadExternalUrlWithFallback(queryId);
            if (!Strings.isNullOrEmpty(loaded)) {
                externalUrlCache.put(queryId, loaded);
            }
            return loaded;
        }
        catch (RuntimeException e) {
            log.warn(e, "Exception while loading external URL for queryId from cache: %s", queryId);
            return null;
        }
    }

    /**
     * Sets external URL in both L1 and L2 caches.
     */
    public void setExternalUrl(String queryId, String externalUrl)
    {
        externalUrlCache.put(queryId, externalUrl);
        if (distributedCache.isEnabled()) {
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }

    /**
     * Batch operation to update all cache entries (backend, routing group, external URL).
     * Updates both L1 and L2 caches.
     */
    public void updateAllCaches(String queryId, String backend, String routingGroup, String externalUrl)
    {
        backendCache.put(queryId, backend);
        routingGroupCache.put(queryId, routingGroup);
        externalUrlCache.put(queryId, externalUrl);
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }

    /**
     * Invalidates all cache entries for the given queryId in both L1 and L2 caches.
     * Useful for cache eviction, troubleshooting, or when query metadata becomes stale.
     */
    public void invalidate(String queryId)
    {
        backendCache.invalidate(queryId);
        routingGroupCache.invalidate(queryId);
        externalUrlCache.invalidate(queryId);
        if (distributedCache.isEnabled()) {
            distributedCache.invalidate(BACKEND_KEY_PREFIX + queryId);
            distributedCache.invalidate(ROUTING_GROUP_KEY_PREFIX + queryId);
            distributedCache.invalidate(EXTERNAL_URL_KEY_PREFIX + queryId);
        }
    }
}
