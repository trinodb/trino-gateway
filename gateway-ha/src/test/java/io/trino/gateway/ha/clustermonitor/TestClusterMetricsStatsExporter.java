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
import org.weakref.jmx.MBeanExporter;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestClusterMetricsStatsExporter
{
    @Test
    void testMetricsRegistrationForNewCluster()
            throws InterruptedException
    {
        try (ClusterMetricsStatsExporter statsExporter = createStatsExporter()) {
            String clusterName1 = "test-cluster1";
            ProxyBackendConfiguration cluster1 = createTestCluster(clusterName1);
            String clusterName2 = "test-cluster2";
            ProxyBackendConfiguration cluster2 = createTestCluster(clusterName2);
            when(statsExporter.getGatewayBackendManager().getAllBackends())
                    .thenReturn(List.of(cluster1)) // First return with 1 cluster
                    .thenReturn(List.of(cluster1, cluster2)); // Then return with 2 clusters to simulate addition

            statsExporter.start();
            Thread.sleep(2000);

            verify(statsExporter.getExporter()).exportWithGeneratedName(
                    argThat(stats -> stats instanceof ClusterMetricsStats && ((ClusterMetricsStats) stats).getClusterName().equals(clusterName1)),
                    eq(ClusterMetricsStats.class), eq(clusterName1));

            // Wait for next update where cluster is added
            Thread.sleep(2000);

            verify(statsExporter.getExporter()).exportWithGeneratedName(
                    argThat(stats -> stats instanceof ClusterMetricsStats && ((ClusterMetricsStats) stats).getClusterName().equals(clusterName2)),
                    eq(ClusterMetricsStats.class), eq(clusterName2));
        }
    }

    @Test
    public void testMetricsUnregistrationForRemovedCluster()
            throws InterruptedException
    {
        try (ClusterMetricsStatsExporter statsExporter = createStatsExporter()) {
            String clusterName = "test-cluster";
            ProxyBackendConfiguration cluster = createTestCluster(clusterName);
            when(statsExporter.getGatewayBackendManager().getAllBackends())
                    .thenReturn(List.of(cluster))  // First return with cluster
                    .thenReturn(List.of());        // Then return empty list to simulate removal

            statsExporter.start();
            Thread.sleep(2000);

            verify(statsExporter.getExporter()).exportWithGeneratedName(
                    argThat(stats -> stats instanceof ClusterMetricsStats && ((ClusterMetricsStats) stats).getClusterName().equals(clusterName)),
                    eq(ClusterMetricsStats.class), eq(clusterName));

            // Wait for next update where cluster is removed
            Thread.sleep(2000);

            verify(statsExporter.getExporter()).unexportWithGeneratedName(eq(ClusterMetricsStats.class), eq(clusterName));
        }
    }

    private static ProxyBackendConfiguration createTestCluster(String name)
    {
        ProxyBackendConfiguration cluster = new ProxyBackendConfiguration();
        cluster.setName(name);
        return cluster;
    }

    private ClusterMetricsStatsExporter createStatsExporter()
    {
        GatewayBackendManager gatewayBackendManager = mock(GatewayBackendManager.class);
        MBeanExporter exporter = mock(MBeanExporter.class);
        MonitorConfiguration monitorConfiguration = mock(MonitorConfiguration.class);

        when(monitorConfiguration.getClusterMetricsRegistryRefreshPeriod())
                .thenReturn(new Duration(1, TimeUnit.SECONDS));

        return new ClusterMetricsStatsExporter(
                gatewayBackendManager,
                exporter,
                monitorConfiguration);
    }
}
