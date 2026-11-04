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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import static io.trino.gateway.ha.clustermonitor.ClusterStatsMonitor.getClusterStatsBuilder;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class ActiveClusterMonitor
{
    public static final int DEFAULT_THREAD_POOL_SIZE = 20;
    private static final Logger log = Logger.get(ActiveClusterMonitor.class);

    private volatile boolean isInitialized;
    private final Set<TrinoClusterStatsObserver> clusterStatsObservers;
    private final GatewayBackendManager gatewayBackendManager;

    private final Duration taskDelay;
    private final ClusterStatsMonitor clusterStatsMonitor;
    private final ExecutorService executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ActiveClusterMonitor(
            Set<TrinoClusterStatsObserver> clusterStatsObservers,
            GatewayBackendManager gatewayBackendManager,
            MonitorConfiguration monitorConfiguration,
            ClusterStatsMonitor clusterStatsMonitor)
    {
        this.clusterStatsMonitor = requireNonNull(clusterStatsMonitor, "clusterStatsMonitor is null");
        this.clusterStatsObservers = requireNonNull(clusterStatsObservers, "clusterStatsObservers is null");
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.taskDelay = monitorConfiguration.getTaskDelay();
    }

    @PostConstruct
    public void start()
    {
        log.info("Running cluster monitor with connection task delay of %s", taskDelay);
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                monitorClusters();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                log.error(e, "Error performing backend monitor tasks");
            }
        }, 0, (long) taskDelay.getValue(), taskDelay.getUnit());
    }

    private void monitorClusters()
            throws InterruptedException
    {
        log.info("Getting stats for all clusters");
        List<ProxyBackendConfiguration> allClusters = gatewayBackendManager.getAllBackends();
        CompletionService<ClusterStats> completionService = new ExecutorCompletionService<>(executorService);
        // Keyed by backend name, which is the cluster ID of the stats
        Map<String, Future<ClusterStats>> futures = new HashMap<>();
        Set<String> startedChecks = ConcurrentHashMap.newKeySet();
        for (ProxyBackendConfiguration backend : allClusters) {
            futures.put(backend.getName(), completionService.submit(() -> {
                startedChecks.add(backend.getName());
                return monitorCluster(backend);
            }));
        }
        // Publish the stats of each cluster as soon as its check completes,
        // so that a slow cluster does not delay the updates of the others
        Set<String> completedChecks = new HashSet<>();
        long deadline = System.nanoTime() + taskDelay.roundTo(NANOSECONDS);
        for (int i = 0; i < futures.size(); i++) {
            Future<ClusterStats> future = completionService.poll(deadline - System.nanoTime(), NANOSECONDS);
            if (future == null) {
                break;
            }
            ClusterStats stats = future.resultNow();
            completedChecks.add(stats.clusterId());
            publish(stats);
        }
        // Do not let checks that take longer than the monitor interval delay the next cycle
        for (ProxyBackendConfiguration backend : allClusters) {
            String name = backend.getName();
            // A check that completed after the deadline is published in the next cycle
            if (completedChecks.contains(name) || !futures.get(name).cancel(true)) {
                continue;
            }
            if (startedChecks.contains(name)) {
                log.warn("Health check of backend %s did not complete within %s", name, taskDelay);
                publish(unhealthyClusterStats(backend));
            }
            else {
                // All monitor threads were busy, so the backend was not checked
                log.warn("Health check of backend %s did not start within %s", name, taskDelay);
            }
        }
        isInitialized = true;
    }

    private ClusterStats monitorCluster(ProxyBackendConfiguration backend)
    {
        try {
            return clusterStatsMonitor.monitor(backend);
        }
        catch (RuntimeException e) {
            log.error(e, "Error checking health of backend %s", backend.getName());
            return unhealthyClusterStats(backend);
        }
    }

    private static ClusterStats unhealthyClusterStats(ProxyBackendConfiguration backend)
    {
        return getClusterStatsBuilder(backend).trinoStatus(TrinoStatus.UNHEALTHY).build();
    }

    private void publish(ClusterStats stats)
    {
        for (TrinoClusterStatsObserver observer : clusterStatsObservers) {
            observer.observe(ImmutableList.of(stats));
        }
    }

    @PreDestroy
    public void stop()
    {
        executorService.shutdownNow();
        scheduledExecutor.shutdownNow();
    }

    public boolean isInitialized()
    {
        return isInitialized;
    }
}
