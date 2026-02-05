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
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.stats.CounterStat;
import io.trino.gateway.ha.config.DatabaseCacheConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.dao.GatewayBackend;
import io.trino.gateway.ha.persistence.dao.GatewayBackendDao;
import org.jdbi.v3.core.Jdbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class HaGatewayManager
        implements GatewayBackendManager
{
    private static final Logger log = Logger.get(HaGatewayManager.class);
    private static final Object ALL_BACKEND_CACHE_KEY = new Object();

    private final GatewayBackendDao dao;
    private final String defaultRoutingGroup;
    private final boolean cacheEnabled;
    private final LoadingCache<Object, List<GatewayBackend>> backendCache;

    private final CounterStat backendLookupSuccesses = new CounterStat();
    private final CounterStat backendLookupFailures = new CounterStat();

    @Inject
    public HaGatewayManager(Jdbi jdbi, RoutingConfiguration routingConfiguration, DatabaseCacheConfiguration databaseCacheConfiguration)
    {
        this(jdbi, routingConfiguration, databaseCacheConfiguration, Ticker.systemTicker());
    }

    @VisibleForTesting
    public HaGatewayManager(Jdbi jdbi, RoutingConfiguration routingConfiguration, DatabaseCacheConfiguration databaseCacheConfiguration, Ticker ticker)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(GatewayBackendDao.class);
        defaultRoutingGroup = routingConfiguration.getDefaultRoutingGroup();
        cacheEnabled = databaseCacheConfiguration.isEnabled();
        if (cacheEnabled) {
            Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
                    .initialCapacity(1)
                    .ticker(ticker);
            if (databaseCacheConfiguration.getExpireAfterWrite() != null) {
                caffeineBuilder = caffeineBuilder.expireAfterWrite(databaseCacheConfiguration.getExpireAfterWrite().toJavaTime());
            }
            if (databaseCacheConfiguration.getRefreshAfterWrite() != null) {
                caffeineBuilder = caffeineBuilder.refreshAfterWrite(databaseCacheConfiguration.getRefreshAfterWrite().toJavaTime());
            }
            backendCache = caffeineBuilder.build(this::fetchAllBackends);

            // Load the data once during initialization. This ensures a fail-fast behavior in case of database misconfiguration.
            try {
                List<GatewayBackend> _ = backendCache.get(ALL_BACKEND_CACHE_KEY);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to warm up backend cache", e);
            }
        }
        else {
            backendCache = null;
        }
    }

    private List<GatewayBackend> fetchAllBackends(Object ignored)
    {
        try {
            List<GatewayBackend> backends = dao.findAll();
            backendLookupSuccesses.update(1);
            return backends;
        }
        catch (Exception e) {
            backendLookupFailures.update(1);
            log.warn(e, "Failed to fetch backends");
            throw e;
        }
    }

    private void invalidateBackendCache()
    {
        if (cacheEnabled) {
            // Avoid using bulk invalidation like invalidateAll(), in order to invalidate in-flight loads properly.
            // See https://github.com/trinodb/trino/issues/10512#issuecomment-1016398117
            backendCache.invalidate(ALL_BACKEND_CACHE_KEY);
        }
    }

    private List<GatewayBackend> getAllBackendsInternal()
    {
        if (cacheEnabled) {
            try {
                return backendCache.get(ALL_BACKEND_CACHE_KEY);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to load backends from database to cache", e);
            }
        }
        else {
            return fetchAllBackends(ALL_BACKEND_CACHE_KEY);
        }
    }

    @Override
    public List<ProxyBackendConfiguration> getAllBackends()
    {
        List<GatewayBackend> proxyBackendList = getAllBackendsInternal();
        return upcast(proxyBackendList);
    }

    @Override
    public List<ProxyBackendConfiguration> getAllActiveBackends()
    {
        List<GatewayBackend> proxyBackendList = getAllBackendsInternal().stream()
                .filter(GatewayBackend::active)
                .collect(toImmutableList());
        return upcast(proxyBackendList);
    }

    @Override
    public List<ProxyBackendConfiguration> getActiveDefaultBackends()
    {
        try {
            return getActiveBackends(defaultRoutingGroup);
        }
        catch (Exception e) {
            log.info("Error fetching backends for default routing group: %s", e.getLocalizedMessage());
        }
        return ImmutableList.of();
    }

    @Override
    public List<ProxyBackendConfiguration> getActiveBackends(String routingGroup)
    {
        List<GatewayBackend> proxyBackendList = getAllBackendsInternal().stream()
                .filter(GatewayBackend::active)
                .filter(backend -> backend.routingGroup().equals(routingGroup))
                .collect(toImmutableList());
        return upcast(proxyBackendList);
    }

    @Override
    public Optional<ProxyBackendConfiguration> getBackendByName(String name)
    {
        List<GatewayBackend> proxyBackendList = getAllBackendsInternal().stream()
                .filter(backend -> backend.name().equals(name))
                .collect(toImmutableList());
        return upcast(proxyBackendList).stream().findAny();
    }

    @Override
    public void deactivateBackend(String backendName)
    {
        updateClusterActivationStatus(backendName, false, () -> dao.deactivate(backendName));
    }

    @Override
    public void activateBackend(String backendName)
    {
        updateClusterActivationStatus(backendName, true, () -> dao.activate(backendName));
    }

    private void updateClusterActivationStatus(String clusterName, boolean newStatus, Runnable changeActiveStatus)
    {
        GatewayBackend model = dao.findFirstByName(clusterName);
        checkState(model != null, "No cluster found with name: %s, could not (de)activate", clusterName);

        boolean previousStatus = model.active();
        changeActiveStatus.run();
        logActivationStatusChange(clusterName, newStatus, previousStatus);
        invalidateBackendCache();
    }

    private static void logActivationStatusChange(String clusterName, boolean newStatus, boolean previousStatus)
    {
        if (previousStatus != newStatus) {
            log.info("Backend cluster %s activation status set to active=%s (previous status: active=%s).", clusterName, newStatus, previousStatus);
        }
    }

    @Override
    public ProxyBackendConfiguration addBackend(ProxyBackendConfiguration backend)
    {
        validateBackendConfiguration(backend);
        String backendProxyTo = removeTrailingSlash(backend.getProxyTo());
        String backendExternalUrl = removeTrailingSlash(backend.getExternalUrl());
        dao.create(backend.getName(), backend.getRoutingGroup(), backendProxyTo, backendExternalUrl, backend.isActive());
        invalidateBackendCache();
        return backend;
    }

    @Override
    public ProxyBackendConfiguration updateBackend(ProxyBackendConfiguration backend)
    {
        validateBackendConfiguration(backend);
        String backendProxyTo = removeTrailingSlash(backend.getProxyTo());
        String backendExternalUrl = removeTrailingSlash(backend.getExternalUrl());
        GatewayBackend model = dao.findFirstByName(backend.getName());
        if (model == null) {
            dao.create(backend.getName(), backend.getRoutingGroup(), backendProxyTo, backendExternalUrl, backend.isActive());
        }
        else {
            dao.update(backend.getName(), backend.getRoutingGroup(), backendProxyTo, backendExternalUrl, backend.isActive());
            logActivationStatusChange(backend.getName(), backend.isActive(), model.active());
        }
        invalidateBackendCache();
        return backend;
    }

    private static void validateBackendConfiguration(ProxyBackendConfiguration backend)
    {
        checkArgument(backend.getName() != null, "Backend name cannot be null");
        checkArgument(backend.getProxyTo() != null, "Backend proxyTo URL cannot be null");
        checkArgument(backend.getRoutingGroup() != null, "Backend routing group cannot be null");
        checkArgument(backend.getExternalUrl() != null, "Backend external url cannot be null");
    }

    public void deleteBackend(String name)
    {
        dao.deleteByName(name);
        invalidateBackendCache();
    }

    private static List<ProxyBackendConfiguration> upcast(List<GatewayBackend> gatewayBackendList)
    {
        List<ProxyBackendConfiguration> proxyBackendConfigurations = new ArrayList<>();
        for (GatewayBackend model : gatewayBackendList) {
            ProxyBackendConfiguration backendConfig = new ProxyBackendConfiguration();
            backendConfig.setActive(model.active());
            backendConfig.setRoutingGroup(model.routingGroup());
            backendConfig.setProxyTo(model.backendUrl());
            backendConfig.setExternalUrl(model.externalUrl());
            backendConfig.setName(model.name());
            proxyBackendConfigurations.add(backendConfig);
        }
        return proxyBackendConfigurations;
    }

    public static String removeTrailingSlash(String url)
    {
        return url.replaceAll("/$", "");
    }
}
