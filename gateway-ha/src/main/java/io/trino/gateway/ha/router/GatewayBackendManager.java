package io.trino.gateway.ha.router;

import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.List;
import java.util.Optional;

public interface GatewayBackendManager
{
    List<ProxyBackendConfiguration> getAllBackends();

    List<ProxyBackendConfiguration> getAllActiveBackends();

    List<ProxyBackendConfiguration> getActiveAdhocBackends();

    List<ProxyBackendConfiguration> getActiveBackends(String routingGroup);

    Optional<ProxyBackendConfiguration> getBackendByName(String name);

    ProxyBackendConfiguration addBackend(ProxyBackendConfiguration backend);

    ProxyBackendConfiguration updateBackend(ProxyBackendConfiguration backend);

    void deactivateBackend(String backendName);

    void activateBackend(String backendName);
}
