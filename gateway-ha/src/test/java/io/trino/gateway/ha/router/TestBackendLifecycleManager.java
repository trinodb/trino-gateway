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

import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
final class TestBackendLifecycleManager
{
    @Mock
    private HaGatewayManager gatewayBackendManager;

    @Mock
    private RoutingManager routingManager;

    @Mock
    private BackendStateManager backendStateManager;

    private BackendLifecycleManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new BackendLifecycleManager(gatewayBackendManager, routingManager, backendStateManager);
    }

    // --- deactivateBackend ---

    @Test
    void testDeactivateHappyPath()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));

        manager.deactivateBackend("backend1");

        InOrder order = inOrder(gatewayBackendManager, routingManager, backendStateManager);
        order.verify(gatewayBackendManager).deactivateBackend("backend1");
        order.verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.UNKNOWN);
        order.verify(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));
    }

    @Test
    void testDeactivateCompensatesWhenRoutingManagerFails()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth("backend1", TrinoStatus.UNKNOWN);

        assertThatThrownBy(() -> manager.deactivateBackend("backend1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("routing update failed");

        verify(gatewayBackendManager).deactivateBackend("backend1");
        verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.HEALTHY);
        verify(gatewayBackendManager).activateBackend("backend1");
    }

    @Test
    void testDeactivateCompensatesWhenBackendStateManagerFails()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));
        doThrow(new RuntimeException("state update failed"))
                .when(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));

        assertThatThrownBy(() -> manager.deactivateBackend("backend1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("state update failed");

        verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.HEALTHY);
        verify(gatewayBackendManager).activateBackend("backend1");
    }

    @Test
    void testDeactivateRemovesHealthWhenNoPreviousState()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth("backend1", TrinoStatus.UNKNOWN);

        assertThatThrownBy(() -> manager.deactivateBackend("backend1"))
                .isInstanceOf(RuntimeException.class);

        verify(routingManager).removeBackEndHealth("backend1");
        verify(gatewayBackendManager).activateBackend("backend1");
    }

    // --- activateBackend ---

    @Test
    void testActivateHappyPath()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.UNKNOWN));

        manager.activateBackend("backend1");

        InOrder order = inOrder(gatewayBackendManager, routingManager, backendStateManager);
        order.verify(gatewayBackendManager).activateBackend("backend1");
        order.verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.PENDING);
        order.verify(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));
    }

    @Test
    void testActivateCompensatesWhenRoutingManagerFails()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.UNKNOWN));
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth("backend1", TrinoStatus.PENDING);

        assertThatThrownBy(() -> manager.activateBackend("backend1"))
                .isInstanceOf(RuntimeException.class);

        verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.UNKNOWN);
        verify(gatewayBackendManager).deactivateBackend("backend1");
    }

    @Test
    void testActivateCompensatesWhenBackendStateManagerFails()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.UNKNOWN));
        doThrow(new RuntimeException("state update failed"))
                .when(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));

        assertThatThrownBy(() -> manager.activateBackend("backend1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("state update failed");

        verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.UNKNOWN);
        verify(gatewayBackendManager).deactivateBackend("backend1");
    }

    @Test
    void testActivateRemovesHealthWhenNoPreviousState()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth("backend1", TrinoStatus.PENDING);

        assertThatThrownBy(() -> manager.activateBackend("backend1"))
                .isInstanceOf(RuntimeException.class);

        verify(routingManager).removeBackEndHealth("backend1");
        verify(gatewayBackendManager).deactivateBackend("backend1");
    }

    // --- addBackend ---

    @Test
    void testAddBackendHappyPath()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend1");
        backend.setActive(true);
        when(gatewayBackendManager.addBackend(backend)).thenReturn(backend);

        manager.addBackend(backend);

        InOrder order = inOrder(gatewayBackendManager, routingManager, backendStateManager);
        order.verify(gatewayBackendManager).addBackend(backend);
        order.verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.PENDING);
        order.verify(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));
    }

    @Test
    void testAddBackendCompensatesWhenRoutingManagerFails()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend1");
        backend.setActive(true);
        when(gatewayBackendManager.addBackend(backend)).thenReturn(backend);
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth(eq("backend1"), any(TrinoStatus.class));

        assertThatThrownBy(() -> manager.addBackend(backend))
                .isInstanceOf(RuntimeException.class);

        verify(gatewayBackendManager).deleteBackend("backend1");
    }

    @Test
    void testAddBackendCompensatesWhenBackendStateManagerFails()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend1");
        backend.setActive(true);
        when(gatewayBackendManager.addBackend(backend)).thenReturn(backend);
        doThrow(new RuntimeException("state update failed"))
                .when(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));

        assertThatThrownBy(() -> manager.addBackend(backend))
                .isInstanceOf(RuntimeException.class);

        verify(gatewayBackendManager).deleteBackend("backend1");
    }

    // --- updateBackend ---

    @Test
    void testUpdateBackendHappyPath()
    {
        ProxyBackendConfiguration oldBackend = new ProxyBackendConfiguration();
        oldBackend.setName("backend1");
        oldBackend.setActive(false);

        ProxyBackendConfiguration newBackend = new ProxyBackendConfiguration();
        newBackend.setName("backend1");
        newBackend.setActive(true);

        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.of(oldBackend));
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.UNKNOWN));
        when(gatewayBackendManager.updateBackend(newBackend)).thenReturn(newBackend);

        manager.updateBackend(newBackend);

        InOrder order = inOrder(gatewayBackendManager, routingManager, backendStateManager);
        order.verify(gatewayBackendManager).updateBackend(newBackend);
        order.verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.PENDING);
        order.verify(backendStateManager).updateStates(eq("backend1"), any(ClusterStats.class));
    }

    @Test
    void testUpdateBackendCompensatesWhenInMemoryUpdateFails()
    {
        ProxyBackendConfiguration oldBackend = new ProxyBackendConfiguration();
        oldBackend.setName("backend1");
        oldBackend.setActive(false);

        ProxyBackendConfiguration newBackend = new ProxyBackendConfiguration();
        newBackend.setName("backend1");
        newBackend.setActive(true);

        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.of(oldBackend));
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.UNKNOWN));
        when(gatewayBackendManager.updateBackend(newBackend)).thenReturn(newBackend);
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth(eq("backend1"), any(TrinoStatus.class));

        assertThatThrownBy(() -> manager.updateBackend(newBackend))
                .isInstanceOf(RuntimeException.class);

        verify(gatewayBackendManager).updateBackend(oldBackend);
    }

    @Test
    void testUpdateBackendDoesNotCompensateWhenNoOldBackendExists()
    {
        ProxyBackendConfiguration newBackend = new ProxyBackendConfiguration();
        newBackend.setName("backend1");
        newBackend.setActive(true);

        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.empty());
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.UNKNOWN));
        when(gatewayBackendManager.updateBackend(newBackend)).thenReturn(newBackend);
        doThrow(new RuntimeException("routing update failed"))
                .when(routingManager).updateBackEndHealth(eq("backend1"), any(TrinoStatus.class));

        assertThatThrownBy(() -> manager.updateBackend(newBackend))
                .isInstanceOf(RuntimeException.class);

        verify(gatewayBackendManager, times(1)).updateBackend(newBackend);
    }

    // --- deleteBackend ---

    @Test
    void testDeleteBackendHappyPath()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend1");

        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.of(backend));
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));

        manager.deleteBackend("backend1");

        InOrder order = inOrder(gatewayBackendManager, routingManager, backendStateManager);
        order.verify(gatewayBackendManager).deleteBackend("backend1");
        order.verify(routingManager).removeBackEndHealth("backend1");
        order.verify(backendStateManager).removeStates("backend1");
    }

    @Test
    void testDeleteBackendCompensatesWhenRoutingManagerCleanupFails()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend1");

        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.of(backend));
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));
        doThrow(new RuntimeException("cleanup failed"))
                .when(routingManager).removeBackEndHealth("backend1");

        assertThatThrownBy(() -> manager.deleteBackend("backend1"))
                .isInstanceOf(RuntimeException.class);

        verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.HEALTHY);
        verify(gatewayBackendManager).addBackend(backend);
    }

    @Test
    void testDeleteBackendCompensatesWhenBackendStateManagerRemoveFails()
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend1");

        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.of(backend));
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));
        doThrow(new RuntimeException("state removal failed"))
                .when(backendStateManager).removeStates("backend1");

        assertThatThrownBy(() -> manager.deleteBackend("backend1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("state removal failed");

        verify(routingManager).updateBackEndHealth("backend1", TrinoStatus.HEALTHY);
        verify(gatewayBackendManager).addBackend(backend);
    }

    @Test
    void testDeleteBackendDoesNotCompensateWhenNoOldBackendExists()
    {
        when(gatewayBackendManager.getBackendByName("backend1")).thenReturn(Optional.empty());
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));
        doThrow(new RuntimeException("cleanup failed"))
                .when(routingManager).removeBackEndHealth("backend1");

        assertThatThrownBy(() -> manager.deleteBackend("backend1"))
                .isInstanceOf(RuntimeException.class);

        verify(gatewayBackendManager, never()).addBackend(any());
    }

    // --- compensation mechanism ---

    @Test
    void testCompensationFailureAttachesSuppressedException()
    {
        when(routingManager.getBackEndHealth("backend1")).thenReturn(Optional.of(TrinoStatus.HEALTHY));
        RuntimeException primary = new RuntimeException("routing update failed");
        doThrow(primary)
                .when(routingManager).updateBackEndHealth("backend1", TrinoStatus.UNKNOWN);
        doThrow(new RuntimeException("rollback failed"))
                .when(gatewayBackendManager).activateBackend("backend1");

        assertThatThrownBy(() -> manager.deactivateBackend("backend1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("routing update failed")
                .satisfies(e -> {
                    assertThatThrownBy(() -> {
                        throw e.getSuppressed()[e.getSuppressed().length - 1];
                    })
                            .hasMessage("rollback failed");
                });
    }
}
