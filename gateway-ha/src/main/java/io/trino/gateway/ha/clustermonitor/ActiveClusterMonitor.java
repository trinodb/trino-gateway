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
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collections;
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
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static io.trino.gateway.ha.clustermonitor.ClusterStatsMonitor.getClusterStatsBuilder;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class ActiveClusterMonitor
{
    private static final Logger log = Logger.get(ActiveClusterMonitor.class);

    private volatile boolean isInitialized;
    private final Set<TrinoClusterStatsObserver> clusterStatsObservers;
    private final GatewayBackendManager gatewayBackendManager;

    private final Duration taskDelay;
    private final ClusterStatsMonitor clusterStatsMonitor;
    private final ExecutorService executorService;
    private final Consumer<List<ProxyBackendConfiguration>> checkOrder;
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ActiveClusterMonitor(
            Set<TrinoClusterStatsObserver> clusterStatsObservers,
            GatewayBackendManager gatewayBackendManager,
            MonitorConfiguration monitorConfiguration,
            ClusterStatsMonitor clusterStatsMonitor)
    {
        // Shuffle the order of the checks in each cycle, see monitorClusters()
        this(clusterStatsObservers, gatewayBackendManager, monitorConfiguration, clusterStatsMonitor, Collections::shuffle);
    }

    /// Creates a monitor with a custom order of the health checks in each cycle.
    ///
    /// @param checkOrder reorders the backends in place before their checks are submitted. The default
    ///         shuffles them. Tests keep the order, so they control which checks wait for a monitor thread.
    @VisibleForTesting
    ActiveClusterMonitor(
            Set<TrinoClusterStatsObserver> clusterStatsObservers,
            GatewayBackendManager gatewayBackendManager,
            MonitorConfiguration monitorConfiguration,
            ClusterStatsMonitor clusterStatsMonitor,
            Consumer<List<ProxyBackendConfiguration>> checkOrder)
    {
        checkArgument(monitorConfiguration.getThreadPoolSize() > 0, "monitor threadPoolSize must be greater than 0");
        this.clusterStatsMonitor = requireNonNull(clusterStatsMonitor, "clusterStatsMonitor is null");
        this.clusterStatsObservers = requireNonNull(clusterStatsObservers, "clusterStatsObservers is null");
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.taskDelay = monitorConfiguration.getTaskDelay();
        this.executorService = Executors.newFixedThreadPool(monitorConfiguration.getThreadPoolSize());
        this.checkOrder = requireNonNull(checkOrder, "checkOrder is null");
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

    /// Runs one cycle of health checks for all backends.
    ///
    /// The checks run on a pool of `threadPoolSize` monitor threads, and the cycle ends after the
    /// monitor `taskDelay`, so that a slow backend cannot delay the next cycle:
    ///
    /// - The stats of each backend are published as soon as its check completes, so a slow backend
    ///   does not delay the status updates of the others.
    /// - A check that throws an exception reports the backend as unhealthy.
    /// - A check that started before the deadline, but did not complete, is cancelled, and the
    ///   backend is reported as unhealthy.
    /// - A check that did not start before the deadline, because all monitor threads were busy, is
    ///   cancelled, and the status of the backend is left unchanged, since the backend was not checked.
    /// - A check that completes after the deadline, before it is cancelled, is still published.
    ///
    /// Cancelling a check frees its monitor thread, which then starts one of the waiting checks.
    /// A real check takes longer than the cleanup, so it is cancelled again before it completes.
    /// Without a change of order, the same backends at the end of the list would wait for a monitor
    /// thread in every cycle, and would never be checked while other backends do not respond. The
    /// checks are therefore submitted in the order of `checkOrder`, which shuffles the backends by
    /// default. A `threadPoolSize` of at least the number of backends lets every check start.
    private void monitorClusters()
            throws InterruptedException
    {
        log.info("Getting stats for all clusters");
        List<ProxyBackendConfiguration> allClusters = new ArrayList<>(gatewayBackendManager.getAllBackends());
        checkOrder.accept(allClusters);
        CompletionService<ClusterStats> completionService = new ExecutorCompletionService<>(executorService);
        // Keyed by backend name, which is the cluster ID of the stats
        Map<String, Future<ClusterStats>> futures = new HashMap<>();
        // System.nanoTime() at which the check of each backend started
        Map<String, Long> checkStartTimes = new ConcurrentHashMap<>();
        long deadline = System.nanoTime() + taskDelay.roundTo(NANOSECONDS);
        for (ProxyBackendConfiguration backend : allClusters) {
            futures.put(backend.getName(), completionService.submit(() -> {
                checkStartTimes.put(backend.getName(), System.nanoTime());
                return monitorCluster(backend);
            }));
        }
        // Publish the stats of each cluster as soon as its check completes,
        // so that a slow cluster does not delay the updates of the others
        Set<String> completedChecks = new HashSet<>();
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
            if (completedChecks.contains(name)) {
                continue;
            }
            Future<ClusterStats> future = futures.get(name);
            if (!future.cancel(true)) {
                // The check completed after the deadline, but before it could be cancelled
                if (future.state() == Future.State.SUCCESS) {
                    publish(future.resultNow());
                }
                continue;
            }
            // A worker freed by cancelling another check may start a queued check during this loop,
            // so only a check that started before the deadline counts as not completing in time
            Long startTime = checkStartTimes.get(name);
            if (startTime != null && startTime - deadline < 0) {
                log.warn("Health check of backend %s did not complete within %s", name, taskDelay);
                publish(unhealthyClusterStats(backend));
            }
            else {
                // All monitor threads were busy until the deadline, so the backend was not checked
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
