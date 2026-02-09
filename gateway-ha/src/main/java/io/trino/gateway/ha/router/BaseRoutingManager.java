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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.airlift.log.Logger;
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

import static java.util.Objects.requireNonNull;

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
    private final QueryCacheManager queryCacheManager;

    public BaseRoutingManager(
            GatewayBackendManager gatewayBackendManager,
            RoutingConfiguration routingConfiguration,
            QueryCacheManager queryCacheManager)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        requireNonNull(routingConfiguration, "routingConfiguration is null");
        this.defaultRoutingGroup = routingConfiguration.getDefaultRoutingGroup();
        this.queryCacheManager = requireNonNull(queryCacheManager, "queryCacheManager is null");
        this.backendToStatus = new ConcurrentHashMap<>();
    }

    /**
     * Provide a strategy to select a backend out of all available backends
     */
    protected abstract Optional<ProxyBackendConfiguration> selectBackend(List<ProxyBackendConfiguration> backends, String user);

    @Override
    public void setBackendForQueryId(String queryId, String backend)
    {
        queryCacheManager.setBackend(queryId, backend);
    }

    @Override
    public void setRoutingGroupForQueryId(String queryId, String routingGroup)
    {
        queryCacheManager.setRoutingGroup(queryId, routingGroup);
    }

    @VisibleForTesting
    void setExternalUrlForQueryId(String queryId, String externalUrl)
    {
        queryCacheManager.setExternalUrl(queryId, externalUrl);
    }

    public void updateQueryIdCache(String queryId, String backend, String routingGroup, String externalUrl)
    {
        queryCacheManager.updateAllCaches(queryId, backend, routingGroup, externalUrl);
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
     * Performs cache lookup with full L1->L2->L3 fallback.
     * Delegates all caching logic to QueryCacheManager.
     * If backend not found in cache/database, searches all backends.
     */
    @Nullable
    @Override
    public String findBackendForQueryId(String queryId)
    {
        String backend = queryCacheManager.getBackend(queryId);
        // If not found in L1/L2/L3, search all backends
        if (Strings.isNullOrEmpty(backend)) {
            log.debug("Unable to find backend mapping for [%s]. Searching for suitable backend", queryId);
            backend = searchAllBackendForQuery(queryId);
        }
        return backend;
    }

    @Nullable
    @Override
    public String findExternalUrlForQueryId(String queryId)
    {
        return queryCacheManager.getExternalUrl(queryId);
    }

    /**
     * Looks up the routing group associated with the queryId with full L1->L2->L3 fallback.
     * Delegates all caching logic to QueryCacheManager.
     */
    @Nullable
    @Override
    public String findRoutingGroupForQueryId(String queryId)
    {
        return queryCacheManager.getRoutingGroup(queryId);
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

    /**
     * This tries to find out which backend may have info about given query id. If not found returns
     * the first healthy backend.
     */
    @VisibleForTesting
    String searchAllBackendForQuery(String queryId)
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
        String fallbackBackend = gatewayBackendManager.getActiveBackends(defaultRoutingGroup).stream()
                .findFirst()
                .map(ProxyBackendConfiguration::getProxyTo)
                .orElseThrow(() -> new IllegalStateException("No active backends available for default routing group: " + defaultRoutingGroup));
        // Cache the fallback backend for future requests
        setBackendForQueryId(queryId, fallbackBackend);
        return fallbackBackend;
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