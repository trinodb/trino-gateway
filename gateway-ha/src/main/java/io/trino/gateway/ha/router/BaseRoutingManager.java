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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.airlift.log.Logger;
import io.trino.gateway.ha.cache.Cache;
import io.trino.gateway.ha.cache.QueryCacheManager;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.HttpMethod;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class performs health check, stats counts for each backend and provides a backend given
 * request object. Default implementation comes here.
 */
public abstract class BaseRoutingManager
        implements RoutingManager
{
    private static final Logger log = Logger.get(BaseRoutingManager.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final GatewayBackendManager gatewayBackendManager;
    private final ConcurrentHashMap<String, TrinoStatus> backendToStatus;
    private final String defaultRoutingGroup;
    private final QueryHistoryManager queryHistoryManager;
    private final QueryCacheManager queryCacheManager;

    public BaseRoutingManager(
            GatewayBackendManager gatewayBackendManager,
            QueryHistoryManager queryHistoryManager,
            RoutingConfiguration routingConfiguration,
            Cache distributedCache)
    {
        this.gatewayBackendManager = gatewayBackendManager;
        this.defaultRoutingGroup = routingConfiguration.getDefaultRoutingGroup();
        this.queryHistoryManager = queryHistoryManager;
        this.queryCacheManager = new QueryCacheManager(
                this::findBackendForUnknownQueryId,
                this::findRoutingGroupForUnknownQueryId,
                this::findExternalUrlForUnknownQueryId,
                distributedCache);
        this.backendToStatus = new ConcurrentHashMap<>();
    }

    /**
     * Provide a strategy to select a backend out of all available backends
     */
    protected abstract Optional<ProxyBackendConfiguration> selectBackend(List<ProxyBackendConfiguration> backends, String user);

    @Override
    public void setBackendForQueryId(String queryId, String backend)
    {
        queryCacheManager.setBackendInL1(queryId, backend);
    }

    @Override
    public void setRoutingGroupForQueryId(String queryId, String routingGroup)
    {
        queryCacheManager.setRoutingGroup(queryId, routingGroup);
    }

    /**
     * Performs routing to a default backend.
     */
    public ProxyBackendConfiguration provideDefaultBackendConfiguration(String user)
    {
        List<ProxyBackendConfiguration> backends = gatewayBackendManager.getActiveDefaultBackends().stream()
                .filter(backEnd -> isBackendHealthy(backEnd.getName()))
                .toList();
        return selectBackend(backends, user).orElseThrow(() -> new IllegalStateException("Number of active backends found zero"));
    }

    /**
     * Performs routing to a given cluster group. This falls back to a default backend, if no scheduled
     * backend is found.
     */
    @Override
    public ProxyBackendConfiguration provideBackendConfiguration(String routingGroup, String user)
    {
        List<ProxyBackendConfiguration> backends = gatewayBackendManager.getActiveBackends(routingGroup).stream()
                .filter(backEnd -> isBackendHealthy(backEnd.getName()))
                .toList();
        return selectBackend(backends, user).orElseGet(() -> provideDefaultBackendConfiguration(user));
    }

    /**
     * Performs cache look up, if a backend not found, it checks with all backends and tries to find
     * out which backend has info about given query id.
     */
    @Nullable
    @Override
    public String findBackendForQueryId(String queryId)
    {
        String backendAddress = null;
        try {
            backendAddress = queryCacheManager.getBackendFromL1(queryId);
        }
        catch (RuntimeException e) {
            log.warn("Exception while loading queryId from cache %s", e.getLocalizedMessage());
        }
        return backendAddress;
    }

    @Nullable
    @Override
    public String findExternalUrlForQueryId(String queryId)
    {
        String externalUrl = null;
        try {
            externalUrl = queryCacheManager.getExternalUrlFromL1(queryId);
        }
        catch (RuntimeException e) {
            log.warn("Exception while loading queryId from cache %s", e.getLocalizedMessage());
        }
        return externalUrl;
    }

    /**
     * Looks up the routing group associated with the queryId in the cache.
     * If it's not in the cache, look up in query history
     */
    @Nullable
    @Override
    public String findRoutingGroupForQueryId(String queryId)
    {
        String routingGroup = null;
        try {
            routingGroup = queryCacheManager.getRoutingGroupFromL1(queryId);
        }
        catch (RuntimeException e) {
            log.warn("Exception while loading queryId from routing group cache %s", e.getLocalizedMessage());
        }
        return routingGroup;
    }

    @Override
    public void updateBackEndHealth(String backendId, TrinoStatus value)
    {
        log.info("backend %s isHealthy %s", backendId, value);
        backendToStatus.put(backendId, value);
    }

    @Override
    public void updateClusterStats(List<ClusterStats> stats)
    {
        for (ClusterStats clusterStats : stats) {
            updateBackEndHealth(clusterStats.clusterId(), clusterStats.trinoStatus());
        }
    }

    @Override
    public void setExternalUrlForQueryId(String queryId, String externalUrl)
    {
        queryCacheManager.setExternalUrl(queryId, externalUrl);
    }

    public void updateQueryIdCache(String queryId, String backend, String routingGroup, String externalUrl)
    {
        queryCacheManager.updateAllCaches(queryId, backend, routingGroup, externalUrl);
    }

    @VisibleForTesting
    String findBackendForUnknownQueryId(String queryId)
    {
        String backend;

        // L2: Check Valkey distributed cache if enabled
        Optional<String> cachedBackend = queryCacheManager.getCachedBackend(queryId);
        if (cachedBackend.isPresent()) {
            backend = cachedBackend.get();
            // Update L1 cache
            queryCacheManager.setBackendInL1(queryId, backend);
            return backend;
        }

        // L3: Check database
        backend = queryHistoryManager.getBackendForQueryId(queryId);
        if (Strings.isNullOrEmpty(backend)) {
            log.debug("Unable to find backend mapping for [%s]. Searching for suitable backend", queryId);
            backend = searchAllBackendForQuery(queryId);
        }
        else {
            // Update L2 cache
            queryCacheManager.cacheBackend(queryId, backend);
        }
        return backend;
    }

    /**
     * This tries to find out which backend may have info about given query id. If not found returns
     * the first healthy backend.
     */
    private String searchAllBackendForQuery(String queryId)
    {
        List<ProxyBackendConfiguration> backends = gatewayBackendManager.getAllBackends();

        Map<String, Future<Integer>> responseCodes = new HashMap<>();
        try {
            for (ProxyBackendConfiguration backend : backends) {
                String target = backend.getProxyTo() + "/v1/query/" + queryId;

                Future<Integer> call =
                        executorService.submit(
                                () -> {
                                    URL url = URI.create(target).toURL();
                                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                    conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5));
                                    conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(5));
                                    conn.setRequestMethod(HttpMethod.HEAD);
                                    return conn.getResponseCode();
                                });
                responseCodes.put(backend.getProxyTo(), call);
            }
            for (Map.Entry<String, Future<Integer>> entry : responseCodes.entrySet()) {
                if (entry.getValue().isDone()) {
                    int responseCode = entry.getValue().get();
                    if (responseCode == 200) {
                        log.info("Found query [%s] on backend [%s]", queryId, entry.getKey());
                        setBackendForQueryId(queryId, entry.getKey());
                        return entry.getKey();
                    }
                }
            }
        }
        catch (Exception e) {
            log.warn("Query id [%s] not found", queryId);
        }
        // Fallback on first active backend if queryId mapping not found.
        return gatewayBackendManager.getActiveBackends(defaultRoutingGroup).stream()
                .findFirst()
                .map(ProxyBackendConfiguration::getProxyTo)
                .orElseThrow(() -> new IllegalStateException("No active backends available for default routing group: " + defaultRoutingGroup));
    }

    /**
     * Attempts to look up the routing group associated with the query id from query history table
     */
    private String findRoutingGroupForUnknownQueryId(String queryId)
    {
        // L2: Check Valkey distributed cache if enabled
        Optional<String> cachedRoutingGroup = queryCacheManager.getCachedRoutingGroup(queryId);
        if (cachedRoutingGroup.isPresent()) {
            String routingGroup = cachedRoutingGroup.get();
            // Update L1 cache
            queryCacheManager.setRoutingGroupInL1(queryId, routingGroup);
            return routingGroup;
        }

        // L3: Check database
        String routingGroup = queryHistoryManager.getRoutingGroupForQueryId(queryId);
        setRoutingGroupForQueryId(queryId, routingGroup);
        return routingGroup;
    }

    /**
     * Attempts to look up the external url associated with the query id from query history table
     */
    private String findExternalUrlForUnknownQueryId(String queryId)
    {
        // L2: Check Valkey distributed cache if enabled
        Optional<String> cachedExternalUrl = queryCacheManager.getCachedExternalUrl(queryId);
        if (cachedExternalUrl.isPresent()) {
            String externalUrl = cachedExternalUrl.get();
            // Update L1 cache
            queryCacheManager.setExternalUrlInL1(queryId, externalUrl);
            return externalUrl;
        }

        // L3: Check database
        String externalUrl = queryHistoryManager.getExternalUrlForQueryId(queryId);
        setExternalUrlForQueryId(queryId, externalUrl);
        return externalUrl;
    }

    private boolean isBackendHealthy(String backendId)
    {
        TrinoStatus status = backendToStatus.getOrDefault(backendId, TrinoStatus.UNKNOWN);
        if (status == TrinoStatus.UNKNOWN) {
            log.warn("Backend health for '%s' is UNKNOWN and not tracked.", backendId);
            return false;
        }
        return status == TrinoStatus.HEALTHY;
    }

    @PreDestroy
    public void shutdown()
    {
        executorService.shutdownNow();
    }
}
