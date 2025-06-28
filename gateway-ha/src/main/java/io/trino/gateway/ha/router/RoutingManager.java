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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.HttpMethod;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class performs health check, stats counts for each backend and provides a backend given
 * request object. Default implementation comes here.
 */
public abstract class RoutingManager
{
    private static final Random RANDOM = new Random();
    private static final Logger log = Logger.get(RoutingManager.class);
    private final LoadingCache<String, String> queryIdBackendCache;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final GatewayBackendManager gatewayBackendManager;
    private final ConcurrentHashMap<String, TrinoStatus> backendToStatus;
    private final LoadingCache<String, String> queryIdRoutingGroupCache;
    private final LoadingCache<String, String> queryIdExternalUrlCache;
    private final QueryHistoryManager queryHistoryManager;

    public RoutingManager(GatewayBackendManager gatewayBackendManager, QueryHistoryManager queryHistoryManager)
    {
        this.gatewayBackendManager = gatewayBackendManager;
        this.queryHistoryManager = queryHistoryManager;
        queryIdBackendCache =
                CacheBuilder.newBuilder()
                        .maximumSize(10000)
                        .expireAfterAccess(30, TimeUnit.MINUTES)
                        .build(
                                new CacheLoader<>()
                                {
                                    @Override
                                    public String load(String queryId)
                                    {
                                        return findBackendForUnknownQueryId(queryId);
                                    }
                                });
        queryIdRoutingGroupCache =
                CacheBuilder.newBuilder()
                        .maximumSize(10000)
                        .expireAfterAccess(30, TimeUnit.MINUTES)
                        .build(
                                new CacheLoader<>()
                                {
                                    @Override
                                    public String load(String queryId)
                                    {
                                        return findRoutingGroupForUnknownQueryId(queryId);
                                    }
                                });
        queryIdExternalUrlCache =
                CacheBuilder.newBuilder()
                        .maximumSize(10000)
                        .expireAfterAccess(30, TimeUnit.MINUTES)
                        .build(
                                new CacheLoader<>()
                                {
                                    @Override
                                    public String load(String queryId)
                                    {
                                        return findExternalUrlForUnknownQueryId(queryId);
                                    }
                                });

        this.backendToStatus = new ConcurrentHashMap<>();
    }

    protected GatewayBackendManager getGatewayBackendManager()
    {
        return gatewayBackendManager;
    }

    public void setBackendForQueryId(String queryId, String backend)
    {
        queryIdBackendCache.put(queryId, backend);
    }

    public void setExternalUrlForQueryId(String queryId, String externalUrl)
    {
        queryIdExternalUrlCache.put(queryId, externalUrl);
    }

    public void setRoutingGroupForQueryId(String queryId, String routingGroup)
    {
        queryIdRoutingGroupCache.put(queryId, routingGroup);
    }

    /**
     * Performs routing to an adhoc backend.
     */
    private ProxyBackendConfiguration provideAdhocBackendConfiguration()
    {
        List<ProxyBackendConfiguration> backends = this.gatewayBackendManager.getActiveAdhocBackends();
        backends.removeIf(backend -> isBackendNotHealthy(backend.getName()));
        if (backends.size() == 0) {
            throw new IllegalStateException("Number of active backends found zero");
        }
        int backendId = Math.abs(RANDOM.nextInt()) % backends.size();
        return backends.get(backendId);
    }

    public String provideAdhocCluster(String user)
    {
        return provideAdhocBackendConfiguration().getProxyTo();
    }

    /**
     * Performs routing to a given cluster group. This falls back to an adhoc backend, if no scheduled
     * backend is found.
     */
    public ProxyBackendConfiguration provideBackendConfiguration(String routingGroup, String user)
    {
        List<ProxyBackendConfiguration> backends =
                gatewayBackendManager.getActiveBackends(routingGroup);
        backends.removeIf(backend -> isBackendNotHealthy(backend.getName()));
        if (backends.isEmpty()) {
            return provideAdhocBackendConfiguration();
        }
        int backendId = Math.abs(RANDOM.nextInt()) % backends.size();
        return backends.get(backendId);
    }

    public String provideClusterForRoutingGroup(String routingGroup, String user)
    {
        return provideBackendConfiguration(routingGroup, user).getProxyTo();
    }

    /**
     * Performs cache look up, if a backend not found, it checks with all backends and tries to find
     * out which backend has info about given query id.
     */
    @Nullable
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

    public void updateBackEndHealth(String backendId, TrinoStatus value)
    {
        log.info("backend %s isHealthy %s", backendId, value);
        backendToStatus.put(backendId, value);
    }

    public void updateBackEndStats(List<ClusterStats> stats)
    {
        for (ClusterStats clusterStats : stats) {
            updateBackEndHealth(clusterStats.clusterId(), clusterStats.trinoStatus());
        }
    }

    /**
     * This tries to find out which backend may have info about given query id. If not found returns
     * the first healthy backend.
     */
    protected String findBackendForUnknownQueryId(String queryId)
    {
        List<ProxyBackendConfiguration> backends = gatewayBackendManager.getAllBackends();

        Map<String, Future<Integer>> responseCodes = new HashMap<>();
        try {
            for (ProxyBackendConfiguration backend : backends) {
                String target = backend.getProxyTo() + "/v1/query/" + queryId;

                Future<Integer> call =
                        executorService.submit(
                                () -> {
                                    URL url = new URL(target);
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
        return gatewayBackendManager.getActiveAdhocBackends().get(0).getProxyTo();
    }

    /**
     * Attempts to look up the routing group associated with the query id from query history table
     */
    protected String findRoutingGroupForUnknownQueryId(String queryId)
    {
        String routingGroup = queryHistoryManager.getRoutingGroupForQueryId(queryId);
        setRoutingGroupForQueryId(queryId, routingGroup);
        return routingGroup;
    }

    /**
     * Attempts to look up the external url associated with the query id from query history table
     */
    protected String findExternalUrlForUnknownQueryId(String queryId)
    {
        String externalUrl = queryHistoryManager.getExternalUrlForQueryId(queryId);
        setExternalUrlForQueryId(queryId, externalUrl);
        return externalUrl;
    }

    // Predicate helper function to remove the backends from the list
    // We are returning the unhealthy (not healthy)
    private boolean isBackendNotHealthy(String backendId)
    {
        if (backendToStatus.isEmpty()) {
            log.error("backends can not be empty");
            return true;
        }
        TrinoStatus status = backendToStatus.get(backendId);
        if (status == null) {
            return true;
        }
        return status != TrinoStatus.HEALTHY;
    }
}
