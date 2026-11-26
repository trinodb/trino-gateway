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

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * This class performs health check, stats counts for each backend and provides a backend given
 * request object. Default implementation comes here.
 */
public abstract class BaseRoutingManager
        implements RoutingManager
{
    private static final Logger log = Logger.get(BaseRoutingManager.class);
    private final GatewayBackendManager gatewayBackendManager;
    private final ConcurrentHashMap<String, TrinoStatus> backendToStatus;
    private final String defaultRoutingGroup;
    private final boolean unavailableGroupFallbackEnabled;
    private final QueryHistoryManager queryHistoryManager;
    private final LoadingCache<String, String> queryIdBackendCache;
    private final LoadingCache<String, String> queryIdRoutingGroupCache;
    private final LoadingCache<String, String> queryIdExternalUrlCache;
    // Stats of each Trino cluster by the coordinator ID that the INFO_API health check reports
    private final ConcurrentHashMap<String, ClusterStats> coordinatorIdToClusterStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> clusterToCoordinatorId = new ConcurrentHashMap<>();

    public BaseRoutingManager(GatewayBackendManager gatewayBackendManager, QueryHistoryManager queryHistoryManager, RoutingConfiguration routingConfiguration)
    {
        this.gatewayBackendManager = gatewayBackendManager;
        this.defaultRoutingGroup = routingConfiguration.getDefaultRoutingGroup();
        this.unavailableGroupFallbackEnabled = routingConfiguration.isUnavailableGroupFallbackEnabled();
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
        return selectBackend(backends, user).orElseThrow(() -> new NoHealthyBackendException("No healthy backend found for default routing group [%s]".formatted(defaultRoutingGroup)));
    }

    /**
     * Performs routing to a given cluster group. If the group has no healthy backend, this falls back
     * to the default routing group unless that fallback is disabled.
     */
    @Override
    public ProxyBackendConfiguration provideBackendConfiguration(String routingGroup, String user)
    {
        List<ProxyBackendConfiguration> backends = gatewayBackendManager.getActiveBackends(routingGroup).stream()
                .filter(backEnd -> isBackendHealthy(backEnd.getName()))
                .toList();
        Optional<ProxyBackendConfiguration> selected = selectBackend(backends, user);
        if (selected.isPresent()) {
            return selected.get();
        }
        if (routingGroup.equals(defaultRoutingGroup)) {
            throw new NoHealthyBackendException("No healthy backend found for default routing group [%s]".formatted(defaultRoutingGroup));
        }
        if (!unavailableGroupFallbackEnabled) {
            throw new NoHealthyBackendException("No healthy backend found for routing group [%s] and fallback to the default routing group is disabled".formatted(routingGroup));
        }
        try {
            return provideDefaultBackendConfiguration(user);
        }
        catch (NoHealthyBackendException e) {
            throw new NoHealthyBackendException("No healthy backend found for routing group [%s] or default routing group [%s]".formatted(routingGroup, defaultRoutingGroup));
        }
    }

    @Override
    public boolean isBackendActive(String backendUrl)
    {
        // The OAuth2 pin is a scheme://authority string: the proxy target without any path
        // (see ProxyRequestHandler#getRemoteTarget). Match each backend on the same shape, case
        // insensitively, so a proxyTo that carries a base path or differs in host case still matches
        // its own pin instead of being treated as a stale backend and forcing a needless re-auth.
        String target = schemeAndAuthority(backendUrl);
        return gatewayBackendManager.getAllActiveBackends().stream()
                .anyMatch(backend -> schemeAndAuthority(backend.getProxyTo()).equals(target));
    }

    @Override
    public Optional<String> findInactiveUnhealthyBackendName(String backendUrl)
    {
        String target = schemeAndAuthority(backendUrl);
        List<ProxyBackendConfiguration> matching = gatewayBackendManager.getAllBackends().stream()
                .filter(backend -> schemeAndAuthority(backend.getProxyTo()).equals(target))
                .toList();
        // Another active backend serving the same address, or one still reporting healthy,
        // means the cluster is not out of service.
        if (matching.stream().anyMatch(backend -> backend.isActive()
                || getBackEndHealth(backend.getName()).orElse(TrinoStatus.UNKNOWN) == TrinoStatus.HEALTHY)) {
            return Optional.empty();
        }
        return matching.stream()
                .map(ProxyBackendConfiguration::getName)
                .findFirst();
    }

    private static String schemeAndAuthority(String url)
    {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() != null && uri.getAuthority() != null) {
                return (uri.getScheme() + "://" + uri.getAuthority()).toLowerCase(Locale.ROOT);
            }
        }
        catch (IllegalArgumentException e) {
            // Not a parseable URL (e.g. a bare host); fall back to a case-insensitive literal match.
        }
        return url.toLowerCase(Locale.ROOT);
    }

    /// Looks up the backend for the query ID in the cache, and on a cache miss in the query history
    /// and by the coordinator ID. Falls back to the first active backend of the default routing group.
    /// The fallback is not cached, so a later request for the same query ID still finds its Trino
    /// cluster once the health check reports the coordinator ID, such as right after a restart.
    @Nullable
    @Override
    public String findBackendForQueryId(String queryId)
    {
        try {
            String backend = queryIdBackendCache.get(queryId);
            if (backend != null) {
                return backend;
            }
            log.debug("Unable to find backend mapping for [%s]. Using the first active backend of the default routing group", queryId);
            return gatewayBackendManager.getActiveBackends(defaultRoutingGroup).stream()
                    .findFirst()
                    .map(ProxyBackendConfiguration::getProxyTo)
                    .orElseThrow(() -> new IllegalStateException("No active backends available for default routing group: " + defaultRoutingGroup));
        }
        catch (RuntimeException e) {
            log.warn("Exception while loading queryId from cache %s", e.getLocalizedMessage());
            return null;
        }
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
        String coordinatorId = clusterToCoordinatorId.remove(backendId);
        if (coordinatorId != null) {
            coordinatorIdToClusterStats.remove(coordinatorId);
        }
    }

    @Override
    public void updateClusterStats(List<ClusterStats> stats)
    {
        for (ClusterStats clusterStats : stats) {
            updateBackEndHealth(clusterStats.clusterId(), clusterStats.trinoStatus());
            updateCoordinatorId(clusterStats);
        }
    }

    private void updateCoordinatorId(ClusterStats clusterStats)
    {
        String coordinatorId = clusterStats.coordinatorId();
        if (coordinatorId == null) {
            return;
        }
        // The coordinator draws a new ID on every start, and queries do not survive a restart
        String previousCoordinatorId = clusterToCoordinatorId.put(clusterStats.clusterId(), coordinatorId);
        if (previousCoordinatorId != null && !previousCoordinatorId.equals(coordinatorId)) {
            coordinatorIdToClusterStats.remove(previousCoordinatorId);
        }
        coordinatorIdToClusterStats.put(coordinatorId, clusterStats);
    }

    @Override
    public void setExternalUrlForQueryId(String queryId, String externalUrl)
    {
        queryIdExternalUrlCache.put(queryId, externalUrl);
    }

    /// Returns `null` when neither the query history nor the coordinator ID knows the query, which
    /// the cache does not store.
    @Nullable
    @VisibleForTesting
    String findBackendForUnknownQueryId(String queryId)
    {
        String backend = queryHistoryManager.getBackendForQueryId(queryId);
        if (Strings.isNullOrEmpty(backend)) {
            return findClusterStatsForCoordinatorId(queryId).map(ClusterStats::proxyTo).orElse(null);
        }
        return backend;
    }

    /**
     * Attempts to look up the routing group associated with the query id from query history table
     */
    private String findRoutingGroupForUnknownQueryId(String queryId)
    {
        String routingGroup = queryHistoryManager.getRoutingGroupForQueryId(queryId);
        if (Strings.isNullOrEmpty(routingGroup)) {
            return findClusterStatsForCoordinatorId(queryId).map(ClusterStats::routingGroup).orElse(null);
        }
        return routingGroup;
    }

    /**
     * Attempts to look up the external url associated with the query id from query history table
     */
    private String findExternalUrlForUnknownQueryId(String queryId)
    {
        String externalUrl = queryHistoryManager.getExternalUrlForQueryId(queryId);
        if (Strings.isNullOrEmpty(externalUrl)) {
            return findClusterStatsForCoordinatorId(queryId).map(ClusterStats::externalUrl).orElse(null);
        }
        return externalUrl;
    }

    /// Finds the Trino cluster that runs a query by the coordinator ID at the end of the query ID. The
    /// map is filled by the `INFO_API` health check for Trino 477 and later, so it is at most one
    /// monitor `taskDelay` old.
    private Optional<ClusterStats> findClusterStatsForCoordinatorId(String queryId)
    {
        int separator = queryId.lastIndexOf('_');
        if (separator < 0) {
            return Optional.empty();
        }
        Optional<ClusterStats> clusterStats = Optional.ofNullable(coordinatorIdToClusterStats.get(queryId.substring(separator + 1)));
        clusterStats.ifPresent(stats -> log.debug("Routing query [%s] to Trino cluster [%s] by its coordinator ID", queryId, stats.clusterId()));
        return clusterStats;
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
}
