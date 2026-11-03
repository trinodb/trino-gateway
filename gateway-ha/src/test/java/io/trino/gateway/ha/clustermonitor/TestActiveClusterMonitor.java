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
package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestActiveClusterMonitor
{
    private static final ProxyBackendConfiguration ACTIVE_BACKEND = createBackend("active", true);
    private static final ProxyBackendConfiguration INACTIVE_BACKEND = createBackend("inactive", false);

    @Test
    void testInactiveBackendsNotCheckedByDefault()
            throws Exception
    {
        assertThat(new MonitorConfiguration().isCheckInactiveBackends()).isFalse();
        assertThat(monitoredClusters(new MonitorConfiguration()))
                .containsExactly(ACTIVE_BACKEND.getName());
    }

    @Test
    void testInactiveBackendsCheckedWhenEnabled()
            throws Exception
    {
        MonitorConfiguration monitorConfiguration = new MonitorConfiguration();
        monitorConfiguration.setCheckInactiveBackends(true);
        assertThat(monitoredClusters(monitorConfiguration))
                .containsExactlyInAnyOrder(ACTIVE_BACKEND.getName(), INACTIVE_BACKEND.getName());
    }

    private static List<String> monitoredClusters(MonitorConfiguration monitorConfiguration)
            throws Exception
    {
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(List.of(ACTIVE_BACKEND, INACTIVE_BACKEND));
        when(backendManager.getAllActiveBackends()).thenReturn(List.of(ACTIVE_BACKEND));

        CompletableFuture<List<ClusterStats>> observed = new CompletableFuture<>();
        TrinoClusterStatsObserver observer = observed::complete;
        ClusterStatsMonitor statsMonitor = backend -> ClusterStats.builder(backend.getName())
                .trinoStatus(TrinoStatus.HEALTHY)
                .build();

        ActiveClusterMonitor monitor = new ActiveClusterMonitor(Set.of(observer), backendManager, monitorConfiguration, statsMonitor);
        monitor.start();
        try {
            return observed.get(10, SECONDS).stream()
                    .map(ClusterStats::clusterId)
                    .toList();
        }
        finally {
            monitor.stop();
        }
    }

    private static ProxyBackendConfiguration createBackend(String name, boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setProxyTo("http://%s.example.com:8080".formatted(name));
        backend.setActive(active);
        return backend;
    }
}
