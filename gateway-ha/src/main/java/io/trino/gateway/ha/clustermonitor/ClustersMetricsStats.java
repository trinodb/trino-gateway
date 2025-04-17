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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.weakref.jmx.MBeanExporter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class ClustersMetricsStats
{
    private static final Logger log = Logger.get(ClustersMetricsStats.class);

    private final MBeanExporter exporter;
    private final GatewayBackendManager gatewayBackendManager;
    private final MonitorConfiguration monitorConfiguration;
    // MBeanExporter uses weak references, so statsMap is needed to maintain strong references to metric objects to prevent garbage collection
    private final Map<String, ClusterMetricsStats> statsMap = new HashMap<>();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ClustersMetricsStats(GatewayBackendManager gatewayBackendManager, MBeanExporter exporter, MonitorConfiguration monitorConfiguration)
    {
        this.gatewayBackendManager = gatewayBackendManager;
        this.exporter = exporter;
        this.monitorConfiguration = monitorConfiguration;
    }

    @PostConstruct
    public void start()
    {
        Duration refreshInterval = monitorConfiguration.getClusterMetricsRegistryRefreshPeriod();
        log.info("Running periodic metric refresh with interval of %s", refreshInterval);
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                updateClustersMetricRegistry();
            }
            catch (Exception e) {
                log.error(e, "Error refreshing cluster metrics");
            }
        }, 0, refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop()
    {
        scheduledExecutor.shutdownNow();
    }

    public synchronized void updateClustersMetricRegistry()
    {
        // Get current clusters from DB
        Set<String> currentClusters = gatewayBackendManager.getAllBackends().stream()
                .map(ProxyBackendConfiguration::getName)
                .collect(Collectors.toSet());

        // Unregister metrics for removed clusters
        for (String registeredCluster : statsMap.keySet()) {
            if (!currentClusters.contains(registeredCluster)) {
                try {
                    exporter.unexportWithGeneratedName(ClusterMetricsStats.class, registeredCluster);
                    log.info("Unregistered metrics for removed cluster: %s", registeredCluster);
                    statsMap.remove(registeredCluster);
                }
                catch (Exception e) {
                    log.error(e, "Failed to unregister metrics for cluster: %s", registeredCluster);
                }
            }
        }

        // Register metrics for added clusters
        for (String cluster : currentClusters) {
            if (!statsMap.containsKey(cluster)) {
                registerClusterMetrics(cluster);
            }
        }
    }

    public synchronized void registerClusterMetrics(String clusterName)
    {
        ClusterMetricsStats stats = new ClusterMetricsStats(clusterName, gatewayBackendManager);

        if (statsMap.putIfAbsent(clusterName, stats) == null) {  // null means the stats didn't exist previously and was inserted
            try {
                exporter.exportWithGeneratedName(stats, ClusterMetricsStats.class, clusterName);
                log.info("Registered metrics for cluster: %s", clusterName);
            }
            catch (Exception e) {
                statsMap.remove(clusterName);
                log.error(e, "Failed to register metrics for cluster: %s", clusterName);
            }
        }
    }
}
