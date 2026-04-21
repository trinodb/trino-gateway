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
package io.trino.gateway.ha.resource;

import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.RoutingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestHaGatewayResource
{
    private HaGatewayManager haGatewayManager;
    private RoutingManager routingManager;
    private BackendStateManager backendStateManager;
    private HaGatewayResource resource;

    @BeforeEach
    void setUp()
    {
        haGatewayManager = mock(HaGatewayManager.class);
        routingManager = mock(RoutingManager.class);
        backendStateManager = mock(BackendStateManager.class);
        resource = new HaGatewayResource(haGatewayManager, routingManager, backendStateManager);
    }

    @Test
    void testAddActiveBackendSetsPendingInHealthCache()
    {
        ProxyBackendConfiguration backend = createBackend("test-cluster", true);
        when(haGatewayManager.addBackend(backend)).thenReturn(backend);

        resource.addBackend(backend);

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.PENDING);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.PENDING));
    }

    @Test
    void testAddInactiveBackendSetsUnhealthyInHealthCache()
    {
        ProxyBackendConfiguration backend = createBackend("test-cluster", false);
        when(haGatewayManager.addBackend(backend)).thenReturn(backend);

        resource.addBackend(backend);

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.UNHEALTHY);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.UNHEALTHY));
    }

    @Test
    void testUpdateActiveBackendSetsPendingInHealthCache()
    {
        ProxyBackendConfiguration backend = createBackend("test-cluster", true);
        when(haGatewayManager.updateBackend(backend)).thenReturn(backend);

        resource.updateBackend(backend);

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.PENDING);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.PENDING));
    }

    @Test
    void testUpdateInactiveBackendSetsUnhealthyInHealthCache()
    {
        ProxyBackendConfiguration backend = createBackend("test-cluster", false);
        when(haGatewayManager.updateBackend(backend)).thenReturn(backend);

        resource.updateBackend(backend);

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.UNHEALTHY);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.UNHEALTHY));
    }

    @Test
    void testRemoveBackendSetsUnhealthyInHealthCache()
    {
        resource.removeBackend("test-cluster");

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.UNHEALTHY);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.UNHEALTHY));
    }

    private static ProxyBackendConfiguration createBackend(String name, boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setProxyTo("http://" + name + ".example.com");
        backend.setExternalUrl("http://" + name + ".external.example.com");
        backend.setRoutingGroup("adhoc");
        backend.setActive(active);
        return backend;
    }
}
