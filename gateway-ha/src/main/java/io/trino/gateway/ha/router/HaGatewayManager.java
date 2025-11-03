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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import io.airlift.log.Logger;
import io.airlift.stats.CounterStat;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.persistence.dao.GatewayBackend;
import io.trino.gateway.ha.persistence.dao.GatewayBackendDao;
import org.jdbi.v3.core.Jdbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

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

    public HaGatewayManager(Jdbi jdbi, RoutingConfiguration routingConfiguration)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(GatewayBackendDao.class);
        this.defaultRoutingGroup = routingConfiguration.getDefaultRoutingGroup();
        if (!routingConfiguration.getDatabaseCacheTTL().isZero()) {
            cacheEnabled = true;
            backendCache = CacheBuilder
                    .newBuilder()
                    .initialCapacity(1)
                    .refreshAfterWrite(routingConfiguration.getDatabaseCacheTTL().toJavaTime())
                    .build(CacheLoader.asyncReloading(
                            CacheLoader.from(this::fetchAllBackends),
                            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())));
            // Load the data once during initialization. This ensures a fail-fast behavior in case of database misconfiguration.
            backendCache.getUnchecked(ALL_BACKEND_CACHE_KEY);
        }
        else {
            cacheEnabled = false;
            backendCache = null;
        }
    }

    private List<GatewayBackend> fetchAllBackends()
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
            backendCache.invalidateAll();
        }
    }

    private List<GatewayBackend> getOrFetchAllBackends()
    {
        if (cacheEnabled) {
            return backendCache.getUnchecked(ALL_BACKEND_CACHE_KEY);
        }
        else {
            return fetchAllBackends();
        }
    }

    @Override
    public List<ProxyBackendConfiguration> getAllBackends()
    {
        List<GatewayBackend> proxyBackendList = getOrFetchAllBackends();
        return upcast(proxyBackendList);
    }

    @Override
    public List<ProxyBackendConfiguration> getAllActiveBackends()
    {
        List<GatewayBackend> proxyBackendList = getOrFetchAllBackends().stream()
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
        List<GatewayBackend> proxyBackendList = getOrFetchAllBackends().stream()
                .filter(GatewayBackend::active)
                .filter(backend -> backend.routingGroup().equals(routingGroup))
                .collect(toImmutableList());
        return upcast(proxyBackendList);
    }

    @Override
    public Optional<ProxyBackendConfiguration> getBackendByName(String name)
    {
        List<GatewayBackend> proxyBackendList = getOrFetchAllBackends().stream()
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
        String backendProxyTo = removeTrailingSlash(backend.getProxyTo());
        String backendExternalUrl = removeTrailingSlash(backend.getExternalUrl());
        dao.create(backend.getName(), backend.getRoutingGroup(), backendProxyTo, backendExternalUrl, backend.isActive());
        invalidateBackendCache();
        return backend;
    }

    @Override
    public ProxyBackendConfiguration updateBackend(ProxyBackendConfiguration backend)
    {
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
