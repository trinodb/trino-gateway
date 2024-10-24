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
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class ActiveClusterMonitor
{
    public static final int MONITOR_TASK_DELAY_SECONDS = 60;
    public static final int DEFAULT_THREAD_POOL_SIZE = 20;
    private static final Logger log = Logger.get(ActiveClusterMonitor.class);

    private final List<TrinoClusterStatsObserver> clusterStatsObservers;
    private final GatewayBackendManager gatewayBackendManager;

    private final int taskDelaySeconds;
    private final ClusterStatsMonitor clusterStatsMonitor;
    private final ExecutorService executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ActiveClusterMonitor(
            List<TrinoClusterStatsObserver> clusterStatsObservers,
            GatewayBackendManager gatewayBackendManager,
            MonitorConfiguration monitorConfiguration,
            ClusterStatsMonitor clusterStatsMonitor)
    {
        this.clusterStatsMonitor = requireNonNull(clusterStatsMonitor, "clusterStatsMonitor is null");
        this.clusterStatsObservers = requireNonNull(clusterStatsObservers, "clusterStatsObservers is null");
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.taskDelaySeconds = monitorConfiguration.getTaskDelaySeconds();
    }

    @PostConstruct
    public void start()
    {
        log.info("Running cluster monitor with connection task delay of %d seconds", taskDelaySeconds);
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                log.info("Getting stats for all active clusters");
                List<ProxyBackendConfiguration> activeClusters =
                        gatewayBackendManager.getAllActiveBackends();
                List<Future<ClusterStats>> futures = new ArrayList<>();
                for (ProxyBackendConfiguration backend : activeClusters) {
                    Future<ClusterStats> call = executorService.submit(() -> clusterStatsMonitor.monitor(backend));
                    futures.add(call);
                }
                List<ClusterStats> stats = new ArrayList<>();
                for (Future<ClusterStats> clusterStatsFuture : futures) {
                    ClusterStats clusterStats = clusterStatsFuture.get();
                    stats.add(clusterStats);
                }

                if (clusterStatsObservers != null) {
                    for (TrinoClusterStatsObserver observer : clusterStatsObservers) {
                        observer.observe(stats);
                    }
                }
            }
            catch (Exception e) {
                log.error(e, "Error performing backend monitor tasks");
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(taskDelaySeconds));
            }
            catch (Exception e) {
                log.error(e, "Error with monitor task");
            }
        }, 0, taskDelaySeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop()
    {
        executorService.shutdownNow();
        scheduledExecutor.shutdownNow();
    }
}
