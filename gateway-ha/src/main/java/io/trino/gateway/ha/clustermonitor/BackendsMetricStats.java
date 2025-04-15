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

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class BackendsMetricStats
{
    private static final Logger log = Logger.get(BackendsMetricStats.class);
    public static final Duration DEFAULT_BACKEND_METRICS_REGISTRY_REFRESH_PERIOD = new Duration(30, SECONDS);

    private final MBeanExporter exporter;
    private final GatewayBackendManager gatewayBackendManager;
    private final MonitorConfiguration monitorConfiguration;
    // MBeanExporter uses weak references, so statsMap is needed to maintain strong references to metric objects to prevent garbage collection
    private final Map<String, BackendClusterMetricStats> statsMap = new HashMap<>();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public BackendsMetricStats(GatewayBackendManager gatewayBackendManager, MBeanExporter exporter, MonitorConfiguration monitorConfiguration)
    {
        this.gatewayBackendManager = gatewayBackendManager;
        this.exporter = exporter;
        this.monitorConfiguration = monitorConfiguration;
    }

    @PostConstruct
    public void start()
    {
        Duration refreshInterval = monitorConfiguration.getBackendMetricsRegistryRefreshPeriod();
        log.info("Running periodic metric refresh with interval of %s", refreshInterval);
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                updateMetrics();
            }
            catch (Exception e) {
                log.error(e, "Error refreshing backend metrics");
            }
        }, 0, refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop()
    {
        scheduledExecutor.shutdownNow();
    }

    public synchronized void updateMetrics()
    {
        // Get current backends from DB
        Set<String> currentBackends = gatewayBackendManager.getAllBackends().stream()
                .map(ProxyBackendConfiguration::getName)
                .collect(Collectors.toSet());

        // Unregister metrics for removed backends
        for (String registeredBackend : statsMap.keySet()) {
            if (!currentBackends.contains(registeredBackend)) {
                try {
                    exporter.unexportWithGeneratedName(BackendClusterMetricStats.class, registeredBackend);
                    log.info("Unregistered metrics for removed cluster: %s", registeredBackend);
                    statsMap.remove(registeredBackend);
                }
                catch (Exception e) {
                    log.error(e, "Failed to unregister metrics for cluster: %s", registeredBackend);
                }
            }
        }

        // Register metrics for added backends
        for (String backend : currentBackends) {
            if (!statsMap.containsKey(backend)) {
                registerBackendMetrics(backend);
            }
        }
    }

    public synchronized void registerBackendMetrics(String clusterName)
    {
        BackendClusterMetricStats stats = new BackendClusterMetricStats(clusterName, gatewayBackendManager);

        if (statsMap.putIfAbsent(clusterName, stats) == null) {  // null means the stats didn't exist previously and was inserted
            try {
                exporter.exportWithGeneratedName(stats, BackendClusterMetricStats.class, clusterName);
                log.info("Registered metrics for cluster: %s", clusterName);
            }
            catch (Exception e) {
                statsMap.remove(clusterName);
                log.error(e, "Failed to register metrics for cluster: %s", clusterName);
            }
        }
    }
}
