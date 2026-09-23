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
import com.google.common.base.Function;
import com.google.common.base.Strings;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.HttpMethod;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
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
    private static final long BACKEND_SEARCH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
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
            backendAddress = queryIdBackendCache.get(queryId);
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
            externalUrl = queryIdExternalUrlCache.get(queryId);
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
            routingGroup = queryIdRoutingGroupCache.get(queryId);
        }
        catch (RuntimeException e) {
            log.warn("Exception while loading queryId from routing group cache %s", e.getLocalizedMessage());
        }
        return routingGroup;
    }

    @Override
    public Optional<TrinoStatus> getBackEndHealth(String backendId)
    {
        return Optional.ofNullable(backendToStatus.get(backendId));
    }

    @Override
    public void updateBackEndHealth(String backendId, TrinoStatus value)
    {
        log.info("backend %s isHealthy %s", backendId, value);
        backendToStatus.put(backendId, value);
    }

    @Override
    public void removeBackEndHealth(String backendId)
    {
        log.info("Removing backend %s from health tracking", backendId);
        backendToStatus.remove(backendId);
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

        CompletionService<ProbeResult> completionService = new ExecutorCompletionService<>(executorService);
        List<Future<ProbeResult>> probes = new ArrayList<>();
        try {
            for (ProxyBackendConfiguration backend : backends) {
                String proxyTo = backend.getProxyTo();
                String target = proxyTo + "/v1/query/" + queryId;

                probes.add(completionService.submit(
                        () -> {
                            try {
                                URL url = URI.create(target).toURL();
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5));
                                conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(5));
                                conn.setRequestMethod(HttpMethod.HEAD);
                                return new ProbeResult(proxyTo, conn.getResponseCode() == 200);
                            }
                            catch (IOException e) {
                                log.debug(e, "Could not check backend [%s] for query [%s]", proxyTo, queryId);
                                return new ProbeResult(proxyTo, false);
                            }
                        }));
            }
            // Read the results in the order the probes finish, against a single shared deadline. Waiting on
            // the futures in submission order would let a slow or hung backend use up the whole budget
            // before a probe that has already answered is looked at. The executor is a fixed pool, so a
            // per probe timeout would also compound when there are more backends than threads.
            long deadline = System.nanoTime() + BACKEND_SEARCH_TIMEOUT_NANOS;
            for (int i = 0; i < probes.size(); i++) {
                // A deadline that has already passed still returns a probe that has finished
                Future<ProbeResult> probe = completionService.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (probe == null) {
                    log.warn("Timed out searching backends for query [%s]", queryId);
                    break;
                }
                try {
                    ProbeResult result = probe.get();
                    if (result.queryFound()) {
                        log.info("Found query [%s] on backend [%s]", queryId, result.backend());
                        setBackendForQueryId(queryId, result.backend());
                        return result.backend();
                    }
                }
                catch (ExecutionException e) {
                    log.debug(e, "Could not check a backend for query [%s]", queryId);
                }
            }
        }
        catch (InterruptedException e) {
            // Keep the interrupt for the caller and fall back like any other failed search
            Thread.currentThread().interrupt();
            log.warn("Interrupted while searching backends for query [%s]", queryId);
        }
        catch (Exception e) {
            log.warn("Query id [%s] not found", queryId);
        }
        finally {
            // Nothing reads the remaining probes. This drops the ones still queued; a probe that is already
            // running keeps its thread until its HTTP timeout, because the socket read is not interruptible.
            probes.forEach(probe -> probe.cancel(true));
        }
        // Fallback on first active backend if queryId mapping not found.
        return gatewayBackendManager.getActiveBackends(defaultRoutingGroup).stream()
                .findFirst()
                .map(ProxyBackendConfiguration::getProxyTo)
                .orElseThrow(() -> new IllegalStateException("No active backends available for default routing group: " + defaultRoutingGroup));
    }

    private record ProbeResult(String backend, boolean queryFound) {}

    /**
     * Attempts to look up the routing group associated with the query id from query history table
     */
    private String findRoutingGroupForUnknownQueryId(String queryId)
    {
        return queryHistoryManager.getRoutingGroupForQueryId(queryId);
    }

    /**
     * Attempts to look up the external url associated with the query id from query history table
     */
    private String findExternalUrlForUnknownQueryId(String queryId)
    {
        return queryHistoryManager.getExternalUrlForQueryId(queryId);
    }

    private LoadingCache<String, String> buildCache(Function<String, String> loader)
    {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build(loader::apply);
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
