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
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

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
                log.info("Getting stats for all clusters");
                List<ProxyBackendConfiguration> allClusters =
                        gatewayBackendManager.getAllBackends();
                List<Future<ClusterStats>> futures = new ArrayList<>();
                for (ProxyBackendConfiguration backend : allClusters) {
                    Future<ClusterStats> call = executorService.submit(() -> clusterStatsMonitor.monitor(backend));
                    futures.add(call);
                }
                // Bound each round by the task delay, so that a slow cluster cannot hold up the others
                long deadline = System.nanoTime() + taskDelay.roundTo(NANOSECONDS);
                List<ClusterStats> stats = new ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    stats.add(getClusterStats(allClusters.get(i), futures.get(i), deadline));
                }

                if (clusterStatsObservers != null) {
                    for (TrinoClusterStatsObserver observer : clusterStatsObservers) {
                        observer.observe(stats);
                    }
                }
                isInitialized = true;
            }
            catch (Exception e) {
                log.error(e, "Error performing backend monitor tasks");
            }
        }, 0, (long) taskDelay.getValue(), taskDelay.getUnit());
    }

    private ClusterStats getClusterStats(ProxyBackendConfiguration backend, Future<ClusterStats> future, long deadline)
            throws InterruptedException
    {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), NANOSECONDS);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Monitoring cluster %s took longer than %s, marking it unhealthy", backend.getName(), taskDelay);
        }
        catch (ExecutionException e) {
            log.error(e.getCause(), "Error monitoring cluster %s, marking it unhealthy", backend.getName());
        }
        return ClusterStatsMonitor.getClusterStatsBuilder(backend)
                .trinoStatus(TrinoStatus.UNHEALTHY)
                .build();
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
