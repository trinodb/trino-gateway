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

import io.airlift.units.Duration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestActiveClusterMonitor
{
    @Test
    void testSlowAndFailingClustersDoNotHoldUpOthers()
            throws Exception
    {
        GatewayBackendManager backendManager = mock(GatewayBackendManager.class);
        when(backendManager.getAllBackends()).thenReturn(List.of(backend("slow"), backend("failing"), backend("healthy")));

        ClusterStatsMonitor clusterStatsMonitor = backend -> switch (backend.getName()) {
            case "slow" -> {
                try {
                    Thread.sleep(SECONDS.toMillis(60));
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                yield ClusterStatsMonitor.getClusterStatsBuilder(backend).trinoStatus(TrinoStatus.HEALTHY).build();
            }
            case "failing" -> throw new IllegalStateException("monitor failure");
            default -> ClusterStatsMonitor.getClusterStatsBuilder(backend).trinoStatus(TrinoStatus.HEALTHY).build();
        };

        CompletableFuture<List<ClusterStats>> observed = new CompletableFuture<>();
        MonitorConfiguration configuration = new MonitorConfiguration();
        configuration.setTaskDelay(new Duration(1, SECONDS));
        ActiveClusterMonitor monitor = new ActiveClusterMonitor(Set.of(observed::complete), backendManager, configuration, clusterStatsMonitor);

        monitor.start();
        try {
            // The round ends after the task delay instead of waiting for the slow cluster
            Map<String, TrinoStatus> statuses = observed.get(10, SECONDS).stream()
                    .collect(toImmutableMap(ClusterStats::clusterId, ClusterStats::trinoStatus));
            assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "slow", TrinoStatus.UNHEALTHY,
                    "failing", TrinoStatus.UNHEALTHY,
                    "healthy", TrinoStatus.HEALTHY));
        }
        finally {
            monitor.stop();
        }
    }

    private static ProxyBackendConfiguration backend(String name)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(name);
        backend.setProxyTo("http://" + name + ".example.com");
        backend.setRoutingGroup("adhoc");
        return backend;
    }
}
