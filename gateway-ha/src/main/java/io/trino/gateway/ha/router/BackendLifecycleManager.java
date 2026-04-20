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

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BackendLifecycleManager
{
    private static final Logger log = Logger.get(BackendLifecycleManager.class);

    private final GatewayBackendManager gatewayBackendManager;
    private final RoutingManager routingManager;
    private final BackendStateManager backendStateManager;

    @Inject
    public BackendLifecycleManager(
            GatewayBackendManager gatewayBackendManager,
            RoutingManager routingManager,
            BackendStateManager backendStateManager)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.routingManager = requireNonNull(routingManager, "routingManager is null");
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
    }

    public void deactivateBackend(String name)
    {
        Optional<TrinoStatus> previousHealth = routingManager.getBackEndHealth(name);
        gatewayBackendManager.deactivateBackend(name);
        try {
            routingManager.updateBackEndHealth(name, TrinoStatus.UNKNOWN);
            backendStateManager.updateStates(name, ClusterStats.builder(name).trinoStatus(TrinoStatus.UNKNOWN).build());
        }
        catch (RuntimeException e) {
            log.error(e, "In-memory update failed after deactivating backend '%s', reverting", name);
            compensate(e, () -> {
                gatewayBackendManager.activateBackend(name);
                revertHealth(name, previousHealth, e);
            });
            throw e;
        }
    }

    public void activateBackend(String name)
    {
        Optional<TrinoStatus> previousHealth = routingManager.getBackEndHealth(name);
        gatewayBackendManager.activateBackend(name);
        try {
            routingManager.updateBackEndHealth(name, TrinoStatus.PENDING);
            backendStateManager.updateStates(name, ClusterStats.builder(name).trinoStatus(TrinoStatus.PENDING).build());
        }
        catch (RuntimeException e) {
            log.error(e, "In-memory update failed after activating backend '%s', reverting", name);
            compensate(e, () -> {
                gatewayBackendManager.deactivateBackend(name);
                revertHealth(name, previousHealth, e);
            });
            throw e;
        }
    }

    public ProxyBackendConfiguration addBackend(ProxyBackendConfiguration backend)
    {
        ProxyBackendConfiguration result = gatewayBackendManager.addBackend(backend);
        try {
            syncBackendState(result.getName(), result.isActive());
        }
        catch (RuntimeException e) {
            log.error(e, "In-memory update failed after adding backend '%s', reverting", result.getName());
            compensate(e, () -> {
                ((HaGatewayManager) gatewayBackendManager).deleteBackend(result.getName());
                revertHealth(result.getName(), Optional.empty(), e);
            });
            throw e;
        }
        return result;
    }

    public ProxyBackendConfiguration updateBackend(ProxyBackendConfiguration backend)
    {
        Optional<ProxyBackendConfiguration> oldBackend = gatewayBackendManager.getBackendByName(backend.getName());
        Optional<TrinoStatus> previousHealth = routingManager.getBackEndHealth(backend.getName());
        ProxyBackendConfiguration result = gatewayBackendManager.updateBackend(backend);
        try {
            syncBackendState(result.getName(), result.isActive());
        }
        catch (RuntimeException e) {
            log.error(e, "In-memory update failed after updating backend '%s', reverting", result.getName());
            if (oldBackend.isPresent()) {
                compensate(e, () -> {
                    gatewayBackendManager.updateBackend(oldBackend.get());
                    revertHealth(result.getName(), previousHealth, e);
                });
            }
            throw e;
        }
        return result;
    }

    public void deleteBackend(String name)
    {
        Optional<ProxyBackendConfiguration> oldBackend = gatewayBackendManager.getBackendByName(name);
        Optional<TrinoStatus> previousHealth = routingManager.getBackEndHealth(name);
        ((HaGatewayManager) gatewayBackendManager).deleteBackend(name);
        try {
            routingManager.removeBackEndHealth(name);
            backendStateManager.removeStates(name);
        }
        catch (RuntimeException e) {
            log.error(e, "In-memory cleanup failed after deleting backend '%s', reverting", name);
            if (oldBackend.isPresent()) {
                compensate(e, () -> {
                    gatewayBackendManager.addBackend(oldBackend.get());
                    revertHealth(name, previousHealth, e);
                });
            }
            throw e;
        }
    }

    private void syncBackendState(String name, boolean active)
    {
        TrinoStatus trinoStatus = active ? TrinoStatus.PENDING : TrinoStatus.UNKNOWN;
        routingManager.updateBackEndHealth(name, trinoStatus);
        backendStateManager.updateStates(name, ClusterStats.builder(name).trinoStatus(trinoStatus).build());
    }

    private static void compensate(RuntimeException cause, Runnable action)
    {
        try {
            action.run();
        }
        catch (RuntimeException e) {
            cause.addSuppressed(e);
        }
    }

    private void revertHealth(String name, Optional<TrinoStatus> previousHealth, RuntimeException originalException)
    {
        try {
            if (previousHealth.isPresent()) {
                routingManager.updateBackEndHealth(name, previousHealth.get());
            }
            else {
                routingManager.removeBackEndHealth(name);
            }
        }
        catch (RuntimeException revertException) {
            originalException.addSuppressed(revertException);
        }
    }
}
