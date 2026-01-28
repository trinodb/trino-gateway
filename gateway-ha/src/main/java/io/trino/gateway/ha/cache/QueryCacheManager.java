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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Manages query-related caches including both L1 (in-memory LoadingCache) and L2 (distributed cache).
 * This class encapsulates all cache operations to provide better separation of concerns.
 */
public class QueryCacheManager
{
    private static final String BACKEND_KEY_PREFIX = "trino:query:backend:";
    private static final String ROUTING_GROUP_KEY_PREFIX = "trino:query:routing_group:";
    private static final String EXTERNAL_URL_KEY_PREFIX = "trino:query:external_url:";

    private final LoadingCache<String, String> queryIdBackendCache;
    private final LoadingCache<String, String> queryIdRoutingGroupCache;
    private final LoadingCache<String, String> queryIdExternalUrlCache;
    private final Cache distributedCache;

    public QueryCacheManager(
            Function<String, String> backendLoader,
            Function<String, String> routingGroupLoader,
            Function<String, String> externalUrlLoader,
            Cache distributedCache)
    {
        this.queryIdBackendCache = buildCache(backendLoader);
        this.queryIdRoutingGroupCache = buildCache(routingGroupLoader);
        this.queryIdExternalUrlCache = buildCache(externalUrlLoader);
        this.distributedCache = requireNonNull(distributedCache, "distributedCache is null");
    }

    private LoadingCache<String, String> buildCache(Function<String, String> loader)
    {
        return CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<>()
                        {
                            @Override
                            public String load(String queryId)
                            {
                                return loader.apply(queryId);
                            }
                        });
    }

    // L1 Cache Operations

    public void setBackendInL1(String queryId, String backend)
    {
        queryIdBackendCache.put(queryId, backend);
    }

    public void setRoutingGroupInL1(String queryId, String routingGroup)
    {
        queryIdRoutingGroupCache.put(queryId, routingGroup);
    }

    public void setExternalUrlInL1(String queryId, String externalUrl)
    {
        queryIdExternalUrlCache.put(queryId, externalUrl);
    }

    public String getBackendFromL1(String queryId)
            throws ExecutionException
    {
        return queryIdBackendCache.get(queryId);
    }

    public String getRoutingGroupFromL1(String queryId)
            throws ExecutionException
    {
        return queryIdRoutingGroupCache.get(queryId);
    }

    public String getExternalUrlFromL1(String queryId)
            throws ExecutionException
    {
        return queryIdExternalUrlCache.get(queryId);
    }

    // L2 Cache Operations

    public void cacheBackend(String queryId, String backend)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(BACKEND_KEY_PREFIX + queryId, backend);
        }
    }

    public void cacheRoutingGroup(String queryId, String routingGroup)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(ROUTING_GROUP_KEY_PREFIX + queryId, routingGroup);
        }
    }

    public void cacheExternalUrl(String queryId, String externalUrl)
    {
        if (distributedCache.isEnabled()) {
            distributedCache.set(EXTERNAL_URL_KEY_PREFIX + queryId, externalUrl);
        }
    }

    public Optional<String> getCachedBackend(String queryId)
    {
        if (distributedCache.isEnabled()) {
            return distributedCache.get(BACKEND_KEY_PREFIX + queryId);
        }
        return Optional.empty();
    }

    public Optional<String> getCachedRoutingGroup(String queryId)
    {
        if (distributedCache.isEnabled()) {
            return distributedCache.get(ROUTING_GROUP_KEY_PREFIX + queryId);
        }
        return Optional.empty();
    }

    public Optional<String> getCachedExternalUrl(String queryId)
    {
        if (distributedCache.isEnabled()) {
            return distributedCache.get(EXTERNAL_URL_KEY_PREFIX + queryId);
        }
        return Optional.empty();
    }

    // Combined Operations (L1 + L2)

    public void setBackend(String queryId, String backend)
    {
        setBackendInL1(queryId, backend);
        cacheBackend(queryId, backend);
    }

    public void setRoutingGroup(String queryId, String routingGroup)
    {
        setRoutingGroupInL1(queryId, routingGroup);
        cacheRoutingGroup(queryId, routingGroup);
    }

    public void setExternalUrl(String queryId, String externalUrl)
    {
        setExternalUrlInL1(queryId, externalUrl);
        cacheExternalUrl(queryId, externalUrl);
    }

    public void updateAllCaches(String queryId, String backend, String routingGroup, String externalUrl)
    {
        setBackendInL1(queryId, backend);
        cacheBackend(queryId, backend);
        cacheRoutingGroup(queryId, routingGroup);
        cacheExternalUrl(queryId, externalUrl);
    }

    public boolean isDistributedCacheEnabled()
    {
        return distributedCache.isEnabled();
    }
}
