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

import com.google.common.annotations.VisibleForTesting;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Singleton
public class ClusterMetricsStatsExporter
        implements AutoCloseable
{
    private static final Logger log = Logger.get(ClusterMetricsStatsExporter.class);

    private final MBeanExporter exporter;
    private final GatewayBackendManager gatewayBackendManager;
    private final Duration refreshInterval;
    // MBeanExporter uses weak references, so clustersStats Map is needed to maintain strong references to metric objects to prevent garbage collection
    private final Map<String, ClusterMetricsStats> clustersStats = new HashMap<>();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ClusterMetricsStatsExporter(GatewayBackendManager gatewayBackendManager, MBeanExporter exporter, MonitorConfiguration monitorConfiguration)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.exporter = requireNonNull(exporter, "exporter is null");
        this.refreshInterval = monitorConfiguration.getClusterMetricsRegistryRefreshPeriod();
    }

    @PostConstruct
    public void start()
    {
        log.debug("Running periodic metric refresh with interval of %s", refreshInterval);
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                updateClustersMetricRegistry();
            }
            catch (Exception e) {
                log.error(e, "Error refreshing cluster metrics");
            }
        }, 0, refreshInterval.toMillis(), MILLISECONDS);
    }

    @PreDestroy
    public void stop()
    {
        scheduledExecutor.shutdownNow();
    }

    private synchronized void updateClustersMetricRegistry()
    {
        // Get current clusters from DB
        Set<String> currentClusters = gatewayBackendManager.getAllBackends().stream()
                .map(ProxyBackendConfiguration::getName)
                .collect(Collectors.toSet());

        // Create a copy of keys to avoid concurrent modification
        Set<String> registeredClusters = new HashSet<>(clustersStats.keySet());

        // Unregister metrics for removed clusters
        for (String registeredCluster : registeredClusters) {
            if (!currentClusters.contains(registeredCluster)) {
                try {
                    exporter.unexportWithGeneratedName(ClusterMetricsStats.class, registeredCluster);
                    log.debug("Unregistered metrics for removed cluster: %s", registeredCluster);
                    clustersStats.remove(registeredCluster);
                }
                catch (Exception e) {
                    log.error(e, "Failed to unregister metrics for cluster: %s", registeredCluster);
                }
            }
        }

        // Register metrics for added clusters
        for (String cluster : currentClusters) {
            if (!clustersStats.containsKey(cluster)) {
                registerClusterMetrics(cluster);
            }
        }
    }

    private synchronized void registerClusterMetrics(String clusterName)
    {
        ClusterMetricsStats stats = new ClusterMetricsStats(clusterName, gatewayBackendManager);

        if (clustersStats.putIfAbsent(clusterName, stats) == null) {  // null means the stats didn't exist previously and was inserted
            try {
                exporter.exportWithGeneratedName(stats, ClusterMetricsStats.class, clusterName);
                log.debug("Registered metrics for cluster: %s", clusterName);
            }
            catch (Exception e) {
                clustersStats.remove(clusterName);
                log.error(e, "Failed to register metrics for cluster: %s", clusterName);
            }
        }
        else {
            log.warn("Attempted to register metrics for duplicate cluster name: %s. This may cause JMX registration issues.", clusterName);
        }
    }

    @VisibleForTesting
    GatewayBackendManager gatewayBackendManager()
    {
        return gatewayBackendManager;
    }

    @VisibleForTesting
    MBeanExporter exporter()
    {
        return exporter;
    }

    @Override
    public void close()
    {
        stop();
    }
}
