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
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

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
    private final LoadingCache<String, String> queryIdBackendCache;
    private final LoadingCache<String, String> queryIdRoutingGroupCache;
    private final LoadingCache<String, String> queryIdExternalUrlCache;

    public BaseRoutingManager(GatewayBackendManager gatewayBackendManager, QueryHistoryManager queryHistoryManager, RoutingConfiguration routingConfiguration)
    {
        this.gatewayBackendManager = gatewayBackendManager;
        this.defaultRoutingGroup = routingConfiguration.getDefaultRoutingGroup();
        this.queryHistoryManager = queryHistoryManager;
        this.queryIdBackendCache = buildCache(this::findBackendForUnknownQueryId);
        this.queryIdRoutingGroupCache = buildCache(this::findRoutingGroupForUnknownQueryId);
        this.queryIdExternalUrlCache = buildCache(this::findExternalUrlForUnknownQueryId);
        this.backendToStatus = new ConcurrentHashMap<>();
    }

    /**
     * Provide a strategy to select a backend out of all available backends
     */
    protected abstract Optional<ProxyBackendConfiguration> selectBackend(List<ProxyBackendConfiguration> backends, String user);

    @Override
    public void setBackendForQueryId(String queryId, String backend)
    {
        queryIdBackendCache.put(queryId, backend);
    }

    @Override
    public void setRoutingGroupForQueryId(String queryId, String routingGroup)
    {
        queryIdRoutingGroupCache.put(queryId, routingGroup);
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
     * Performs routing to a given cluster group. This falls back to a default backend if the target group
     * has no suitable backend unless {@code enforceIsolation} is true, in which case a 404 is returned.
     */
    @Override
    public ProxyBackendConfiguration provideBackendConfiguration(String routingGroup, String user, Boolean enforceIsolation)
    {
        List<ProxyBackendConfiguration> backends = gatewayBackendManager.getActiveBackends(routingGroup).stream()
                .filter(backEnd -> isBackendHealthy(backEnd.getName()))
                .toList();
        if (backends.isEmpty() && enforceIsolation) {
            throw new WebApplicationException(
                             Response.status(NOT_FOUND)
                            .entity(String.format("No healthy backends available for routing group '%s' under enforced isolation for user '%s'", routingGroup, user))
                            .build());
        }
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
            backendAddress = queryIdBackendCache.get(queryId);
        }
        catch (ExecutionException e) {
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
            externalUrl = queryIdExternalUrlCache.get(queryId);
        }
        catch (ExecutionException e) {
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
            routingGroup = queryIdRoutingGroupCache.get(queryId);
        }
        catch (ExecutionException e) {
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

    @VisibleForTesting
    void setExternalUrlForQueryId(String queryId, String externalUrl)
    {
        queryIdExternalUrlCache.put(queryId, externalUrl);
    }

    @VisibleForTesting
    String findBackendForUnknownQueryId(String queryId)
    {
        String backend;
        backend = queryHistoryManager.getBackendForQueryId(queryId);
        if (Strings.isNullOrEmpty(backend)) {
            log.debug("Unable to find backend mapping for [%s]. Searching for suitable backend", queryId);
            backend = searchAllBackendForQuery(queryId);
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
        String routingGroup = queryHistoryManager.getRoutingGroupForQueryId(queryId);
        setRoutingGroupForQueryId(queryId, routingGroup);
        return routingGroup;
    }

    /**
     * Attempts to look up the external url associated with the query id from query history table
     */
    private String findExternalUrlForUnknownQueryId(String queryId)
    {
        String externalUrl = queryHistoryManager.getExternalUrlForQueryId(queryId);
        setExternalUrlForQueryId(queryId, externalUrl);
        return externalUrl;
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
