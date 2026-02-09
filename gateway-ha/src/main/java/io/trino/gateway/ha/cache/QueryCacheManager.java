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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.airlift.log.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Manages all cache operations for query metadata using a 3-tier caching strategy:
 * <ul>
 * <li>L1: Local Caffeine cache (per instance, in-memory)</li>
 * <li>L2: Distributed cache (Valkey/Redis - shared across instances)</li>
 * <li>L3: Database/fallback loader</li>
 * </ul>
 *
 * <p>This refactored design uses a single cache storing {@link QueryMetadata} objects
 * instead of three separate caches for backend, routing group, and external URL.
 * This reduces cache operations by 3x, ensures atomicity, and improves consistency.
 *
 * <p>Benefits:
 * <ul>
 * <li>Single cache lookup instead of three</li>
 * <li>Atomic updates - all metadata updated together</li>
 * <li>One network roundtrip to Valkey instead of three</li>
 * <li>Reduced memory overhead</li>
 * <li>Simpler code and easier maintenance</li>
 * </ul>
 */
public class QueryCacheManager
{
    private static final Logger log = Logger.get(QueryCacheManager.class);

    private final DistributedCache distributedCache;
    private final Cache<String, QueryMetadata> localCache;
    private final QueryCacheLoader cacheLoader;

    /**
     * Interface for loading data from L3 (database/external source) when not found in caches.
     */
    public interface QueryCacheLoader
    {
        /**
         * Load complete query metadata from database or external source.
         * May return null or a QueryMetadata with some null fields if not fully available.
         */
        QueryMetadata loadFromDatabase(String queryId);
    }

    public QueryCacheManager(DistributedCache distributedCache, QueryCacheLoader cacheLoader)
    {
        this.distributedCache = requireNonNull(distributedCache, "distributedCache is null");
        this.cacheLoader = requireNonNull(cacheLoader, "cacheLoader is null");
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Gets complete query metadata with full L1→L2→L3 fallback.
     * Returns empty Optional if not found in any tier.
     *
     * @param queryId the query identifier
     * @return Optional containing QueryMetadata if found, empty otherwise
     */
    public Optional<QueryMetadata> get(String queryId)
    {
        requireNonNull(queryId, "queryId is null");

        // L1: Check local cache first
        QueryMetadata cached = localCache.getIfPresent(queryId);
        if (cached != null) {
            log.debug("Query metadata for [%s] found in L1 cache", queryId);
            return Optional.of(cached);
        }

        // L2: Check distributed cache
        if (distributedCache.isEnabled()) {
            Optional<QueryMetadata> l2Result = distributedCache.get(queryId);
            if (l2Result.isPresent()) {
                log.debug("Query metadata for [%s] found in L2 cache", queryId);
                // Backfill L1
                localCache.put(queryId, l2Result.get());
                return l2Result;
            }
        }

        // L3: Check database
        try {
            QueryMetadata loaded = cacheLoader.loadFromDatabase(queryId);
            if (loaded != null && !loaded.isEmpty()) {
                log.debug("Query metadata for [%s] found in L3 (database)", queryId);
                // Backfill L2 and L1
                localCache.put(queryId, loaded);
                if (distributedCache.isEnabled()) {
                    distributedCache.set(queryId, loaded);
                }
                return Optional.of(loaded);
            }
        }
        catch (RuntimeException e) {
            log.warn(e, "Exception while loading query metadata for queryId from database: %s", queryId);
        }

        return Optional.empty();
    }

    /**
     * Sets complete query metadata in both L1 and L2 caches.
     * This is the preferred method for updating cache with all metadata at once.
     *
     * @param queryId the query identifier
     * @param metadata the complete query metadata
     */
    public void set(String queryId, QueryMetadata metadata)
    {
        requireNonNull(queryId, "queryId is null");
        requireNonNull(metadata, "metadata is null");

        localCache.put(queryId, metadata);
        if (distributedCache.isEnabled()) {
            distributedCache.set(queryId, metadata);
        }
        log.debug("Stored query metadata for [%s] in cache", queryId);
    }

    /**
     * Updates query metadata using copy-on-write pattern.
     * Retrieves existing metadata (with full L1→L2→L3 fallback), merges with the partial update, and stores back.
     * This enables partial updates while maintaining atomicity and consistency across all cache tiers.
     *
     * <p>Example: updating only the backend
     * <pre>
     * cacheManager.update(queryId, QueryMetadata.withBackend(newBackend));
     * </pre>
     *
     * @param queryId the query identifier
     * @param partialMetadata the partial metadata to merge (non-null fields will override)
     */
    public void update(String queryId, QueryMetadata partialMetadata)
    {
        requireNonNull(queryId, "queryId is null");
        requireNonNull(partialMetadata, "partialMetadata is null");

        // Get existing metadata with full L1→L2→L3 fallback for consistency
        QueryMetadata existing = get(queryId).orElse(null);
        QueryMetadata merged;

        if (existing != null) {
            // Merge with existing
            merged = existing.merge(partialMetadata);
            log.debug("Updated query metadata for [%s] via merge", queryId);
        }
        else {
            // No existing data, use partial as-is
            merged = partialMetadata;
            log.debug("Created new query metadata for [%s] from partial update", queryId);
        }

        // Store merged result
        set(queryId, merged);
    }

    /**
     * Gets backend URL for a query.
     * Convenience method that extracts backend from QueryMetadata.
     *
     * @param queryId the query identifier
     * @return the backend URL, or null if not found
     */
    public String getBackend(String queryId)
    {
        return get(queryId).map(QueryMetadata::backend).orElse(null);
    }

    /**
     * Gets routing group for a query.
     * Convenience method that extracts routing group from QueryMetadata.
     *
     * @param queryId the query identifier
     * @return the routing group, or null if not found
     */
    public String getRoutingGroup(String queryId)
    {
        return get(queryId).map(QueryMetadata::routingGroup).orElse(null);
    }

    /**
     * Gets external URL for a query.
     * Convenience method that extracts external URL from QueryMetadata.
     *
     * @param queryId the query identifier
     * @return the external URL, or null if not found
     */
    public String getExternalUrl(String queryId)
    {
        return get(queryId).map(QueryMetadata::externalUrl).orElse(null);
    }

    /**
     * Sets only the backend for a query using partial update.
     * Existing routing group and external URL are preserved.
     *
     * @param queryId the query identifier
     * @param backend the backend URL
     */
    public void setBackend(String queryId, String backend)
    {
        requireNonNull(backend, "backend is null");
        update(queryId, QueryMetadata.withBackend(backend));
    }

    /**
     * Sets only the routing group for a query using partial update.
     * Existing backend and external URL are preserved.
     *
     * @param queryId the query identifier
     * @param routingGroup the routing group
     */
    public void setRoutingGroup(String queryId, String routingGroup)
    {
        requireNonNull(routingGroup, "routingGroup is null");
        update(queryId, QueryMetadata.withRoutingGroup(routingGroup));
    }

    /**
     * Sets only the external URL for a query using partial update.
     * Existing backend and routing group are preserved.
     *
     * @param queryId the query identifier
     * @param externalUrl the external URL
     */
    public void setExternalUrl(String queryId, String externalUrl)
    {
        requireNonNull(externalUrl, "externalUrl is null");
        update(queryId, QueryMetadata.withExternalUrl(externalUrl));
    }

    /**
     * Batch update method that sets all metadata fields at once.
     * This is a convenience method that creates a QueryMetadata object and delegates to set().
     *
     * @param queryId the query identifier
     * @param backend the backend URL
     * @param routingGroup the routing group
     * @param externalUrl the external URL
     */
    public void updateAllCaches(String queryId, String backend, String routingGroup, String externalUrl)
    {
        set(queryId, new QueryMetadata(backend, routingGroup, externalUrl));
    }

    /**
     * Invalidates all cache entries for the given queryId in both L1 and L2 caches.
     * Useful for cache eviction, troubleshooting, or when query metadata becomes stale.
     *
     * @param queryId the query identifier
     */
    public void invalidate(String queryId)
    {
        requireNonNull(queryId, "queryId is null");
        localCache.invalidate(queryId);
        if (distributedCache.isEnabled()) {
            distributedCache.invalidate(queryId);
        }
        log.debug("Invalidated query metadata for [%s]", queryId);
    }
}
