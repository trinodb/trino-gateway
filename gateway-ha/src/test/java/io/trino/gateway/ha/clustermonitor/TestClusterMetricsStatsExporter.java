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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestClusterMetricsStatsExporter
{
    private GatewayBackendManager gatewayBackendManager;
    private MBeanExporter exporter;
    private MonitorConfiguration monitorConfiguration;
    private ClusterMetricsStatsExporter statsExporter;

    @BeforeEach
    void setup()
    {
        gatewayBackendManager = mock(GatewayBackendManager.class);
        exporter = mock(MBeanExporter.class);
        monitorConfiguration = mock(MonitorConfiguration.class);

        when(monitorConfiguration.getClusterMetricsRegistryRefreshPeriod())
                .thenReturn(new Duration(1, TimeUnit.SECONDS));

        statsExporter = new ClusterMetricsStatsExporter(
                gatewayBackendManager,
                exporter,
                monitorConfiguration);
    }

    @Test
    void testMetricsRegistrationForNewCluster()
            throws InterruptedException
    {
        String clusterName1 = "test-cluster1";
        ProxyBackendConfiguration cluster1 = createTestCluster(clusterName1);
        String clusterName2 = "test-cluster2";
        ProxyBackendConfiguration cluster2 = createTestCluster(clusterName2);
        when(gatewayBackendManager.getAllBackends())
                .thenReturn(List.of(cluster1)) // First return with 1 cluster
                .thenReturn(List.of(cluster1, cluster2)); // Then return with 2 clusters to simulate addition

        statsExporter.start();
        Thread.sleep(2000);

        verify(exporter).exportWithGeneratedName(
                argThat(stats -> stats instanceof ClusterMetricsStats && ((ClusterMetricsStats) stats).getClusterName().equals(clusterName1)),
                eq(ClusterMetricsStats.class), eq(clusterName1));

        // Wait for next update where cluster is added
        statsExporter.start();
        Thread.sleep(2000);

        verify(exporter).exportWithGeneratedName(
                argThat(stats -> stats instanceof ClusterMetricsStats && ((ClusterMetricsStats) stats).getClusterName().equals(clusterName2)),
                eq(ClusterMetricsStats.class), eq(clusterName2));
    }

    @Test
    public void testMetricsUnregistrationForRemovedCluster()
            throws InterruptedException
    {
        String clusterName = "test-cluster";
        ProxyBackendConfiguration cluster = createTestCluster(clusterName);
        when(gatewayBackendManager.getAllBackends())
                .thenReturn(List.of(cluster))  // First return with cluster
                .thenReturn(List.of());        // Then return empty list to simulate removal

        statsExporter.start();
        Thread.sleep(2000);

        verify(exporter).exportWithGeneratedName(
                argThat(stats -> stats instanceof ClusterMetricsStats && ((ClusterMetricsStats) stats).getClusterName().equals(clusterName)),
                eq(ClusterMetricsStats.class), eq(clusterName));

        // Wait for next update where cluster is removed
        Thread.sleep(2000);

        verify(exporter).unexportWithGeneratedName(eq(ClusterMetricsStats.class), eq(clusterName));
    }

    private static ProxyBackendConfiguration createTestCluster(String name)
    {
        ProxyBackendConfiguration cluster = new ProxyBackendConfiguration();
        cluster.setName(name);
        return cluster;
    }
}
