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
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.RoutingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

final class TestGatewayResource
{
    private GatewayBackendManager gatewayBackendManager;
    private RoutingManager routingManager;
    private BackendStateManager backendStateManager;
    private GatewayResource resource;

    @BeforeEach
    void setUp()
    {
        gatewayBackendManager = mock(GatewayBackendManager.class);
        routingManager = mock(RoutingManager.class);
        backendStateManager = mock(BackendStateManager.class);
        resource = new GatewayResource(gatewayBackendManager, routingManager, backendStateManager);
    }

    @Test
    void testActivateBackendSetsPendingInHealthCache()
    {
        resource.activateBackend("test-cluster");

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.PENDING);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.PENDING));
    }

    @Test
    void testDeactivateBackendSetsUnhealthyInHealthCache()
    {
        resource.deactivateBackend("test-cluster");

        verify(routingManager).updateBackEndHealth("test-cluster", TrinoStatus.UNHEALTHY);
        verify(backendStateManager).updateStates(eq("test-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.UNHEALTHY));
    }

    @Test
    void testActivateBackendDoesNotUpdateHealthCacheOnError()
    {
        doThrow(new RuntimeException("backend not found")).when(gatewayBackendManager).activateBackend("missing-cluster");

        resource.activateBackend("missing-cluster");

        verify(routingManager, never()).updateBackEndHealth(eq("missing-cluster"), eq(TrinoStatus.PENDING));
        verify(backendStateManager, never()).updateStates(eq("missing-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.PENDING));
    }

    @Test
    void testDeactivateBackendDoesNotUpdateHealthCacheOnError()
    {
        doThrow(new RuntimeException("backend not found")).when(gatewayBackendManager).deactivateBackend("missing-cluster");

        resource.deactivateBackend("missing-cluster");

        verify(routingManager, never()).updateBackEndHealth(eq("missing-cluster"), eq(TrinoStatus.UNHEALTHY));
        verify(backendStateManager, never()).updateStates(eq("missing-cluster"), argThat(stats -> stats.trinoStatus() == TrinoStatus.UNHEALTHY));
    }
}
