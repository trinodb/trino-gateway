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

import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.dao.GatewayBackend;
import io.trino.gateway.ha.persistence.dao.GatewayBackendDao;
import org.jdbi.v3.core.Jdbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class HaGatewayManager
        implements GatewayBackendManager
{
    private final GatewayBackendDao dao;
    private final AtomicReference<List<GatewayBackend>> cache = new AtomicReference<>();

    public HaGatewayManager(Jdbi jdbi)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(GatewayBackendDao.class);
        cache.set(ImmutableList.of());
        fetchAllBackendsToCache();
    }

    @Override
    public List<ProxyBackendConfiguration> getAllBackends()
    {
        List<GatewayBackend> proxyBackendList = cache.get();
        return upcast(proxyBackendList);
    }

    @Override
    public List<ProxyBackendConfiguration> getAllActiveBackends()
    {
        List<GatewayBackend> proxyBackendList = cache.get().stream()
                .filter(GatewayBackend::active)
                .collect(toImmutableList());
        return upcast(proxyBackendList);
    }

    @Override
    public List<ProxyBackendConfiguration> getActiveAdhocBackends()
    {
        return getActiveBackends("adhoc");
    }

    @Override
    public List<ProxyBackendConfiguration> getActiveBackends(String routingGroup)
    {
        List<GatewayBackend> proxyBackendList = cache.get().stream()
                .filter(GatewayBackend::active)
                .filter(backend -> backend.routingGroup().equals(routingGroup))
                .collect(toImmutableList());
        return upcast(proxyBackendList);
    }

    @Override
    public Optional<ProxyBackendConfiguration> getBackendByName(String name)
    {
        List<GatewayBackend> proxyBackendList = cache.get().stream()
                .filter(backend -> backend.name().equals(name))
                .collect(toImmutableList());
        return upcast(proxyBackendList).stream().findAny();
    }

    @Override
    public void deactivateBackend(String backendName)
    {
        dao.deactivate(backendName);
        fetchAllBackendsToCache();
    }

    @Override
    public void activateBackend(String backendName)
    {
        dao.activate(backendName);
        fetchAllBackendsToCache();
    }

    @Override
    public ProxyBackendConfiguration addBackend(ProxyBackendConfiguration backend)
    {
        dao.create(backend.getName(), backend.getRoutingGroup(), backend.getProxyTo(), backend.getExternalUrl(), backend.isActive());
        fetchAllBackendsToCache();
        return backend;
    }

    @Override
    public ProxyBackendConfiguration updateBackend(ProxyBackendConfiguration backend)
    {
        GatewayBackend model = dao.findFirstByName(backend.getName());
        if (model == null) {
            dao.create(backend.getName(), backend.getRoutingGroup(), backend.getProxyTo(), backend.getExternalUrl(), backend.isActive());
        }
        else {
            dao.update(backend.getName(), backend.getRoutingGroup(), backend.getProxyTo(), backend.getExternalUrl(), backend.isActive());
        }
        fetchAllBackendsToCache();
        return backend;
    }

    public void deleteBackend(String name)
    {
        dao.deleteByName(name);
        fetchAllBackendsToCache();
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

    private void fetchAllBackendsToCache()
    {
        cache.set(dao.findAll());
    }
}
